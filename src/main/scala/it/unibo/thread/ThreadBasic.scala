package it.unibo.thread

class Blinker(times: Int) extends Thread:
  override def run(): Unit = (0 to times).view
    .tapEach(_ => Thread.sleep(500))
    .foreach(_ => println("blink!"))
@main def threadBasics: Unit =
  val thread = Thread(() => println("Here")) // as in Java
  thread.start() // starts the thread...
  val oopThread = Blinker(10)
  oopThread.start()

@main def join: Unit =
  val blinkerA = Blinker(10)
  val blinkerB = Blinker(10)
  println("Start A")
  blinkerA.start()
  blinkerA.join()
  println("Start B")
  blinkerB.start()
  blinkerB.join()
