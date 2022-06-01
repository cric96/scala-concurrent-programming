package it.unibo.effect

import monix.catnap.Semaphore
import monix.catnap.MVar
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import scala.concurrent.duration.Duration
import concurrent.duration.DurationInt
import scala.concurrent.Await
import scala.language.postfixOps
import scala.util.Random

@main def synchronization(): Unit =
  val semaphore = Semaphore[Task](1)
  var shared = 0 // Just an example, in effect you should not use vars.. consider a shared resources..
  def effect: Task[Unit] = Task {
    shared += 1
    println(Thread.currentThread().getName)
  }

  val syncComputation = for {
    synch <- semaphore
    tasks = (1 to 1000).map(_ => synch.withPermit(effect))
    par <- Task.parSequence(tasks)
  } yield par

  syncComputation.runSyncUnsafe()
  assert(shared == 1000)

@main def sharedVar(): Unit =
  val myVariable = MVar[Task].empty[Int]()

  def consumer(name: String, data: MVar[Task, Int]) = for {
    _ <- Task(println(s"Try take $name"))
    content <- data.take
    _ <- Task {
      println(s"New data! $content")
    }
    _ <- Task.sleep(Random.between(100, 200) milliseconds)
  } yield ()

  def producer(data: MVar[Task, Int]) = for {
    _ <- Task(println("Try put"))
    result <- data.put(Random.nextInt())
    _ <- Task.sleep(Random.between(100, 200) milliseconds)
  } yield ()

  val execution = for {
    data <- myVariable
    _ <- Task.parZip3(
      consumer("gianluca", data).loopForever,
      consumer("bibo", data).loopForever,
      producer(data).loopForever
    )
  } yield ()

  Await.ready(execution.runToFuture, Duration.Inf)
