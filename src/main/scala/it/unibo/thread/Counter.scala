package it.unibo.thread

import java.util.concurrent.atomic.AtomicInteger

/** monitor interface */
trait Counter:
  def tick(): Unit
  def value: Int

object Counter:
  def safe(): Counter = new Counter:
    private var count = 0 // encapsulated state
    // synchronized is not a keyword.. so you cannot write synchronized def tick ...
    def tick(): Unit = this.synchronized(count += 1)
    def value = this.synchronized(count)

  def unsafe(): Counter = new Counter:
    private var count = 0
    def tick(): Unit = count += 1
    def value = count

  def usingAtomic(): Counter = new Counter:
    val atomicInt = AtomicInteger()
    def tick(): Unit = atomicInt.incrementAndGet()
    def value = atomicInt.get()
object UpdateAgent:
  def apply(times: Int, counter: Counter): Thread = new Thread(() => (1 to times).foreach(_ => counter.tick()))

private val core = Runtime.getRuntime.availableProcessors()
private val n = 10000

def testCounter(counter: Counter): Unit =
  val thread = (1 to core).map(_ => UpdateAgent.apply(n, counter))
  thread.foreach(_.start())
  thread.foreach(_.join()) // synch part...
  println(counter.value)
  assert(counter.value == n * core)

@main def withSafe(): Unit =
  testCounter(Counter.safe())

@main def withUnsafe(): Unit =
  testCounter(Counter.unsafe())

@main def withAtomic(): Unit =
  testCounter(Counter.usingAtomic())
