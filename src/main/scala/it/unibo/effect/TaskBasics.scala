package it.unibo.effect

import com.sun.source.tree.ContinueTree
import monix.catnap.Semaphore
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.Scheduler.Implicits.global

import concurrent.duration.{Duration, DurationInt}
import scala.concurrent.Await
import scala.concurrent.Future
import scala.io.{Source, StdIn}
import scala.language.postfixOps
import scala.util.Random // Global monix context
/** Task is a data type for controlling possibly lazy & asynchronous computations, useful for controlling side-effects,
  * avoiding nondeterminism and callback-hell.
  *
  * Similar to IO (cats) https://typelevel.org/cats-effect/docs/2.x/datatypes/io but with steroid
  */

@main def basics(): Unit =
  /** Tasks are lazy, i.e., they **express** effects => the same computation can be evaluated multiple time */
  val task = Task {
    println(Thread.currentThread().getName)
    1 + 1
  }
  Thread.sleep(1000)
  task.runSyncUnsafe() // Differently from Future, I should explicitly run Tasks
  task.runAsync { // Either expresses a data that could be of two type
    case Right(ok) => println(ok)
    case Left(ko) => println(ko)
  }
  task.runToFuture.foreach(println) // Future can be used as a mechanism to run tasks

@main def cancelComputation(): Unit =
  val execution = Task(true).delayExecution(1 seconds)
  val future = execution.runAsync { case any =>
    println(any)
  }
  Thread.sleep(500)
  future.cancel() // nothing happen, the task is not executed
  execution.foreach(println) // i can use foreach to for the task executed
  for elem <- execution do
    println(
      elem
    ) // Pay attention, for with yield is different from for with do.. the first one does not need execution context!!
  Thread.sleep(2000)

@main def creation(): Unit = // More details on https://monix.io/docs/current/eval/task.html#simple-builders
  val now = Task.now {
    println("Here")
    Math.random()
  } // it evaluates the value asap and wraps it inside the task
  now.foreach(println)
  now.foreach(println)
  //Task.apply =:= Task.eval
  val standard = Task(Math.random()) // lazy.. the evaluation happen when the task is created
  standard.foreach(println)
  standard.foreach(println)
  // Similar to a lazy val, the value it is evaluated once and then memoized (like future)
  val once = Task.evalOnce {
    println("Here")
    Math.random()
  }
  Thread.sleep(500)
  once.foreach(println)
  once.foreach(println)
  val future = Future(println("Here"))
  val fromFuture = Task.fromFuture(future)
  future.foreach(_ => ())
  fromFuture.foreach(_ => ())

@main def taskExecutionFineControl: Unit =
  lazy val io = Scheduler.io(name = "my-io")
  def nothing[E]: E => Unit = _ => ()
  val foo = Task(println(Thread.currentThread().getName))
  foo.foreach(nothing) // run on main
  val async = foo.executeAsync
  val fork = foo.executeOn(io)
  async.foreach(nothing) // force to run into another thread
  foo.executeOn(io).foreach(nothing)
  // multi executors
  val combine = for {
    _ <- foo
    _ <- async
    _ <- fork
  } yield ()
  combine.foreach(nothing)
/** unless you’re doing blocking I/O, keep using the default thread-pool, with global being a good default. For blocking
  * I/O it is OK to have a second thread-pool, but isolate those I/O operations and only override the scheduler for
  * actual I/O operations.
  */

@main def memoize: Unit =
  val random = Task.eval(Math.random())
  random.foreach(println)
  random.foreach(println)
  val memo = random.memoize
  memo.foreach(println)
  memo.foreach(println)
  random.foreach(println)

@main def combine: Unit =
  val file = Task(Source.fromFile("build.sbt"))
  def linesFromSource(source: Source): Task[List[String]] = Task(source.getLines().toList)
  // Sequence evaluation
  val result = for {
    build <- file
    lines <- linesFromSource(build)
  } yield lines.mkString("\n")

  result.foreach(println)
  Thread.sleep(1000)

@main def parallel: Unit =
  val wait = Task.sleep(1 seconds)

  val before = System.currentTimeMillis()
  for
    _ <- wait
    _ <- wait
    _ <- wait
  do println("Sequential... " + (System.currentTimeMillis() - before))

  val parallelCount = System.currentTimeMillis()
  for _ <- Task.parZip3(wait, wait, wait) do println("Parallel.. " + (System.currentTimeMillis() - parallelCount))

  Task
    .sequence(wait :: wait :: wait :: Nil)
    .foreach(_ => println("sequential..."))
  Task
    .parSequence(wait :: wait :: wait :: Nil)
    .foreach(_ => println("parallel.."))
  //.runAsync // Parallel computation
  Thread.sleep(5000)

@main def exceptionHandling: Unit =
  // Task could fail..
  val fail = Task.raiseError(new IllegalStateException("..."))
  fail.runAsync(println(_))
  // Task recover
  val recovered = fail.onErrorHandle(_ => 10)
  recovered.runAsync(println(_))
  Thread.sleep(500)

@main def expressComputation: Unit =
  val random = Task.eval(Math.random())
  val restartComputation = random.tapEval(data => Task(println(s"attempt : $data"))).restartUntil(_ > 0.95)
  println(
    restartComputation.runSyncUnsafe()
  ) // Even if the task restarts, the result is one! (differently from Observables..)

  // I can express a computation like in a scala, but wrapping everything using Task
  enum State:
    case Continue, End
  val input = Task.eval(StdIn.readLine())
  val hello = Task.evalOnce(println("Hello!! welcome to this beautiful game!!"))
  lazy val parse: Task[Int] = input
    .map(_.toInt)
    .onErrorHandleWith(_ =>
      Task(println("Insert a number!!"))
        .flatMap(_ => parse)
    )
  val toGuess = Task.evalOnce(Random.nextInt(10))
  val toHigh = Task(println("The number is wrong!! (high)")).map(_ => State.Continue)
  val toLow = Task(println("The number is wrong!! (low)")).map(_ => State.Continue)
  val correct = Task(println("You won!!")).map(_ => State.End)

  // The main, a typical way to express IO in functional program, through flatmap operations..
  val game = for {
    _ <- hello
    number <- toGuess
    user <- parse
    state <-
      if (user < number) toLow else if (user > number) toHigh else correct
  } yield state

  val futureGame = game.restartUntil(_ != State.Continue).runToFuture
  println(Await.result(futureGame, Duration.Inf))

  // Infinite computation

  val infiniteLoop = Task(println("Hello guys, I can loop forever!!"))
    .delayResult(1 seconds)
    .loopForever

  infiniteLoop.runSyncUnsafe()
