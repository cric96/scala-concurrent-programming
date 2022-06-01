package it.unibo.collections

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext

trait SharedList:
  def put(element: Int): Unit
  def get(): List[Int]

class SafeSharedList extends SharedList:
  private val reference: AtomicReference[List[Int]] = AtomicReference(List.empty)
  def put(element: Int): Unit = reference.updateAndGet(list => element :: list)
  def get(): List[Int] = reference.get()

class UnsafeSharedList extends SharedList:
  private var reference: List[Int] = List.empty
  def put(element: Int): Unit = reference = element :: reference
  def get(): List[Int] = reference

def checkList(list: SharedList): Unit =
  val execution = ExecutionContext.global
  (1 to 100).foreach(i => execution.execute(() => list.put(i)))

@main def unsafe(): Unit =
  val unsafeList = new UnsafeSharedList
  checkList(unsafeList)
  Thread.sleep(1000) // kind of sync.. we will see how to sync async computations
  assert(unsafeList.get().size == 100)

@main def safe(): Unit =
  val safeList = new SafeSharedList
  checkList(safeList)
  Thread.sleep(1000) // kind of sync.. we will see how to sync async computations
  assert(safeList.get().size == 100)
