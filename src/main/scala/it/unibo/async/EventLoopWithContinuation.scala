package it.unibo.async

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ConcurrentLinkedQueue, LinkedBlockingDeque, LinkedBlockingQueue}
import scala.concurrent.{Await, Future, Promise}
import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

trait DataMagnet:
  type A
  def complete(): Unit
  def future: Future[A]

object DataMagnet:
  def wrap[W](computation: => W, promise: Promise[W]): DataMagnet = new DataMagnet:
    override type A = W
    override def future: Future[A] = promise.future
    override def complete(): Unit = promise.success(computation)

class EventLoopWithContinuation:
  private val events = LinkedBlockingQueue[DataMagnet]
  private val executed = AtomicReference(true)

  def process[E](computation: => E): Future[E] =
    val promise = Promise[E]()
    val magnet = DataMagnet.wrap(computation, promise)
    events.offer(magnet)
    promise.future

  def start(): Unit =
    new Thread(() =>
      while (executed.get())
        events.take().complete()
    ).start()

  def destroy(): Unit =
    events.offer(DataMagnet.wrap((), Promise())) // process the last evetn
    executed.set(false)

@main def testEventLoop(): Unit =
  val loop = EventLoopWithContinuation()
  loop.start()
  val executedInLoop = loop.process(10).map(_ * 2).map(_.toString)
  executedInLoop.foreach(println(_))
  Await.ready(executedInLoop, Duration.Inf)
  loop.destroy()
