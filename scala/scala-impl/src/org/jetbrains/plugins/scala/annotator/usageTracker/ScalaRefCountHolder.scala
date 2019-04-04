package org.jetbrains.plugins.scala
package annotator
package usageTracker

import java.{util => ju}

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.{DaemonCodeAnalyzer, impl}
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{LowMemoryWatcher, Ref, TextRange}
import com.intellij.psi._
import com.intellij.util.containers.{ContainerUtil, hash}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.05.2010
 */

/**
 * See com.intellij.codeInsight.daemon.impl.RefCountHolder
 */
final class ScalaRefCountHolder private() {

  import ScalaRefCountHolder._
  import State._

  private val myState = new ju.concurrent.atomic.AtomicReference(VIRGIN)

  private val myImportUsed = ContainerUtil.newConcurrentSet[ImportUsed]
  private val myValueUsed = ContainerUtil.newConcurrentSet[ValueUsed]

  def registerImportUsed(used: ImportUsed): Unit = {
    myImportUsed.add(used)
  }

  def registerValueUsed(used: ValueUsed): Unit = {
    myValueUsed.add(used)
  }

  def usageFound(used: ImportUsed): Boolean = {
    assertState()
    myImportUsed.contains(used)
  }

  def isValueWriteUsed(element: PsiNamedElement): Boolean = isValueUsed {
    WriteValueUsed(element)
  }

  def isValueReadUsed(element: PsiNamedElement): Boolean = isValueUsed {
    ReadValueUsed(element)
  }

  private def isValueUsed(used: ValueUsed): Boolean = {
    assertState()
    myValueUsed.contains(used)
  }

  def analyze(analyze: Runnable, file: PsiFile): Boolean = {
    val dirtyScope = findDirtyScope(file)(file.getProject)
    myState.compareAndSet(READY, VIRGIN)

    if (myState.compareAndSet(VIRGIN, WRITE)) {
      try {
        assertState(WRITE)

        val defaultRange = Some(file.getTextRange)
        dirtyScope.getOrElse(defaultRange) match {
          case `defaultRange` =>
            myImportUsed.clear()
            myValueUsed.clear()
          case Some(_) =>
            clear(myImportUsed)(_.element.isValid)
            clear(myValueUsed)(_.isValid)
          case _ =>
        }

        analyze.run()
      } finally {
        setReady(WRITE)
      }

      true
    } else {
      false
    }
  }


  def retrieveUnusedReferencesInfo(analyze: () => Unit): Boolean =
    if (myState.compareAndSet(READY, READ)) {
      try {
        analyze()
      } finally {
        setReady(READ)
      }

      true
    } else {
      false
    }

  private def setReady(expect: Int): Unit = {
    val value = myState.compareAndSet(expect, READY)
    Log.assertTrue(value, myState.get)
  }

  private def assertState(expected: Int = READ): Unit = {
    val actual = myState.get
    Log.assertTrue(actual == expected, actual)
  }
}

object ScalaRefCountHolder {

  private val Log = Logger.getInstance(getClass)

  private object State {
    val VIRGIN = 0
    val WRITE = 1
    val READY = 2
    val READ = 3
  }

  def apply(element: PsiNamedElement): ScalaRefCountHolder =
    getInstance(element.getContainingFile)

  def getInstance(file: PsiFile): ScalaRefCountHolder = {
    import extensions.FileViewProviderExt
    file.getViewProvider
      .findScalaPsi
      .getOrElse(file)
      .getProject
      .getComponent(classOf[ScalaRefCountHolderComponent])
      .getOrCreate(
        file.getName + file.hashCode,
        new ScalaRefCountHolder
      )
  }

  def findDirtyScope(file: PsiFile)
                    (implicit project: Project): Option[Option[TextRange]] =
    PsiDocumentManager.getInstance(project).getDocument(file) match {
      case null => None
      case document =>
        DaemonCodeAnalyzer.getInstance(project) match {
          case analyzerImpl: impl.DaemonCodeAnalyzerImpl =>
            Some(Option(analyzerImpl.getFileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL)))
          case _ => None
        }
    }

  private def clear[T](used: ju.Set[T])
                      (isValid: T => Boolean): Unit = used.synchronized {
    val valuesIterator = used.iterator
    while (valuesIterator.hasNext) {
      if (!isValid(valuesIterator.next)) {
        valuesIterator.remove()
      }
    }
  }
}

final class ScalaRefCountHolderComponent(project: Project) extends ProjectComponent {

  import ScalaRefCountHolderComponent._

  private val autoCleaningMap = Ref.create[TimestampedValueMap[String, ScalaRefCountHolder]]

  override def projectOpened(): Unit = {
    autoCleaningMap.set(new TimestampedValueMap())

    JobScheduler.getScheduler.scheduleWithFixedDelay(
      cleanupRunnable(autoCleaningMap),
      CleanupDelay,
      CleanupDelay,
      ju.concurrent.TimeUnit.MILLISECONDS
    )

    LowMemoryWatcher.register(cleanupRunnable(autoCleaningMap), project)
  }

  override def projectClosed(): Unit = autoCleaningMap.set(null)

  def getOrCreate(key: String, holder: => ScalaRefCountHolder): ScalaRefCountHolder = {
    autoCleaningMap.get match {
      case null => holder
      case map => map.getOrCreate(key, holder)
    }
  }
}

object ScalaRefCountHolderComponent {

  import concurrent.duration._

  private val CleanupDelay = 1.minute.toMillis

  private def cleanupRunnable(map: Ref[_ <: TimestampedValueMap[_, _]]): Runnable = () => map.get.removeStaleEntries()

  final private[usageTracker] class TimestampedValueMap[K, V](minimumSize: Int = 3,
                                                              maximumSize: Int = 20,
                                                              storageTime: Long = 5.minutes.toMillis) {

    private[this] case class Timestamped(value: V, var timestamp: Long = -1)

    private[this] val innerMap = new hash.LinkedHashMap[K, Timestamped](maximumSize, true) {

      override def removeEldestEntry(eldest: ju.Map.Entry[K, Timestamped]): Boolean = size() > maximumSize

      override def doRemoveEldestEntry(): Unit = this.synchronized {
        super.doRemoveEldestEntry()
      }
    }

    def getOrCreate(key: K, value: => V): V = innerMap.synchronized {
      val timestamped = innerMap.get(key) match {
        case null =>
          val newValue = Timestamped(value)
          innerMap.put(key, newValue)
          newValue
        case cached => cached
      }

      timestamped.timestamp = System.currentTimeMillis()
      timestamped.value
    }

    def removeStaleEntries(): Unit = innerMap.synchronized {
      innerMap.size - minimumSize match {
        case diff if diff > 0 =>
          val timeDiff = System.currentTimeMillis() - storageTime

          import collection.JavaConverters._
          innerMap.entrySet
            .iterator
            .asScala
            .take(diff)
            .filter(_.getValue.timestamp < timeDiff)
            .map(_.getKey)
            .foreach(innerMap.remove)
        case _ =>
      }
    }
  }

}