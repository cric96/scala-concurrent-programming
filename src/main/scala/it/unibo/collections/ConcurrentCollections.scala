package it.unibo.collections

import java.util.concurrent.{ConcurrentLinkedQueue, Executors, LinkedBlockingQueue}
import scala.concurrent.ExecutionContext

class EventLoop():
  private val loop = new Thread(() =>
    while (true)
      toRun.peek().apply()
  )
  private val toRun: LinkedBlockingQueue[() => Unit] = LinkedBlockingQueue()
  def on(action: => Unit): Unit = toRun.add(() => action)

@main def useLoop: Unit =
  var counter = 0
  val loop = EventLoop()
  (1 to 100) foreach { _ => loop.on(counter += 1) }
  loop.on(assert(counter == 100))
