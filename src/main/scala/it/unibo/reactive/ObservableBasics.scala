package it.unibo.reactive

import monix.reactive.{Consumer, MulticastStrategy, Observable, OverflowStrategy}
import monix.execution.Scheduler.Implicits.global
import monix.eval.Task
import monix.execution.Cancelable
import monix.reactive.subjects.ConcurrentSubject

import concurrent.duration.{Duration, DurationInt}
import java.awt.event.ActionEvent
import javax.swing.{JButton, JFrame}
import scala.concurrent.Await
import scala.io.Source
import scala.language.postfixOps

/** the Observable is a data type for modeling and processing asynchronous and reactive streaming of events with
  * non-blocking back-pressure.
  *
  * NB! It is not pure: contains side effects interface, Iterant is the pure interface
  */
@main def observableBasics: Unit =
  val observable = Observable // Like iterable API but ...
    .fromIterable(1 to 3)
    .map(_ + 2)
    .map(_ * 3)

  val result = observable.sumL // It returns an async computation
  result.foreach(println)
  Thread.sleep(500)
  // Or you can consume the stream with an ad-hoc object
  val consumer: Consumer[Int, Int] = Consumer.foldLeft(0)(_ + _)

  val resultWithConsumer = observable.consumeWith(consumer)
  println(resultWithConsumer.runSyncUnsafe())

/** Creation.. */
@main def createObservable: Unit =
  val single = Observable.pure { println("hey!"); 10 } // print hey! because the value is evaluated asap
  val delay = Observable.delay { println("hey"); 10 } // hey is printed each time the observable is consumed
  delay.foreach(println)
  delay.foreach(println)
  val lazyObservable = Observable.evalOnce { println("hey"); 10 } // hey is printed once, then the value is memoized
  lazyObservable.foreach(println)
  lazyObservable.foreach(println)
  val sideEffects = Observable.suspend:
      val effect = Source.fromFile("build.sbt")
      Observable.fromIterator(Task(effect.getLines()))
  sideEffects.foreach(println)
  Thread.sleep(500)

// How to create observable from old api (or in general to create new observables..)
@main def facade: Unit =
  val button = JButton("Click me!!")
  val frame = new JFrame("Hello!")
  frame.getContentPane.add(button)
  frame.pack()
  frame.setVisible(true)
  // Using create api
  val buttonObservable = Observable.create[Long](OverflowStrategy.Unbounded): subject =>
      button.addActionListener((e: ActionEvent) => subject.onNext(e.getWhen))
      Cancelable.empty

  // Or through subjects (i.e., tuple of observer and observable)
  val subject = ConcurrentSubject[Long](MulticastStrategy.replay)
  button.addActionListener((e: ActionEvent) => subject.onNext(e.getWhen))
  buttonObservable.foreach(when => println(s"Observable : $when"))
  subject.foreach(when => println(s"subject $when"))

/** Observable have a monadic interfaces, therefore they can be composed */
@main def composition: Unit =
  val stepper = Observable.fromIterable(LazyList.continually(10)).delayOnNext(500 milliseconds)
  val greeter = Observable.repeatEval("Hello!!").delayOnNext(100 milliseconds)
  val combined = for // I can combine observable like list, option,....
    number <- stepper
    greet <- greeter
  yield (number, greet)
  combined.foreach(println(_))
  Thread.sleep(1000)

@main def errorHandling: Unit =
  val errorObs = Observable.raiseError(new IllegalStateException("Ops.."))
  val withObs =
    errorObs.onErrorFallbackTo(Observable(1)) // I can "flatMap" observable to other obs in case of failures..
  val withValues = errorObs.onErrorHandle(_ => 1) // Or I can "handle" the error directly..

@main def asyncAndBackpressure: Unit =
  val obs = Observable("A", "B", "C", "D", "E", "F", "G")
  val syncObs = obs // By default, the obs manipulation are sequential
    .mapEval(elem => Task { println(s"Processing (1) : $elem"); elem + elem })
    .mapEval(elem => Task { println(s"Processing (2) : $elem"); elem }.delayExecution(200 milliseconds))

  val syncFuture = syncObs.completedL.runToFuture // force the obs evaluation
  Await.ready(syncFuture, Duration.Inf)
  // I can force the computation to be concurrent / async => I need a backpressure strategy!
  def testStrategy(strategy: OverflowStrategy[String]): Unit =
    println(s"Strategy: $strategy")
    val asyncObs = obs // By default, the obs manipulation are sequential
      .mapEval(elem => Task { println(s"Processing (1) : $elem"); elem + elem }.delayExecution(10 milliseconds))
      .asyncBoundary(strategy)
      .mapEval(elem => Task { println(s"Processing (2) : $elem"); elem }.delayExecution(200 milliseconds))

    val asyncFuture = asyncObs.completedL.runToFuture
    Await.ready(asyncFuture, Duration.Inf)
  testStrategy(OverflowStrategy.Unbounded) // Could have memory leak problems
  testStrategy(OverflowStrategy.BackPressure(2)) // Slow down the down up stream
  testStrategy(OverflowStrategy.DropNew(2)) // Drop (newest) elements when the buffer is full
  testStrategy(OverflowStrategy.DropOld(2)) // Drop (oldest) elements when the buffer is full

// Buffer operations https://monix.io/docs/current/reactive/observable.html#processing-elements-in-batches
@main def buffers: Unit =
  val base = Observable.fromIterable(0 to 10).delayOnNext(100 milliseconds)
  base
    .bufferSliding(2, 1)
    .foreachL(println(_))
    .runSyncUnsafe() // (0, 1); (1, 2) ...
// Time based
  base
    .bufferTimed(500 milliseconds)
    .foreachL(println(_))
    .runSyncUnsafe()
  // Condition based
  base
    .bufferWhile(_ < 5)
    .foreachL(println(_))
    .runSyncUnsafe()

// Manage "source" using time abstraction https://monix.io/docs/current/reactive/observable.html#limiting-the-rate-of-emission-of-elements
// Useful => slow down inputs
@main def rateEmission: Unit =
  val base = Observable.fromIterable(0 to 10)
  println("Throttle")
  base.throttle(100 milliseconds, 1).foreachL(println).runSyncUnsafe()
  println("Throttle first")
  base.throttleFirst(100 milliseconds).foreachL(println).runSyncUnsafe() // emit the oldest event in the time window
  println("Throttle last (sample)")
  // emit the most recent event in the time window (sample)
  base.throttleLast(100 milliseconds).foreachL(println).runSyncUnsafe()

// Parallel execution, like par in scala collection
@main def parallel: Unit =
  val element = Observable.fromIterable(0 to 10)
  val parallelMap = element.mapParallelOrdered(8)(elem =>
    Task(elem * 5).tapEval(id => Task(println(id + " " + Thread.currentThread().getName)))
  )
  parallelMap.foreachL(println(_)).runSyncUnsafe() // passed in order..
  val parallelUnorderedMap = element.mapParallelUnordered(8)(elem =>
    Task(elem * 5).tapEval(id => Task(println(id + " " + Thread.currentThread().getName)))
  )
  parallelUnorderedMap.foreachL(println(_)).runSyncUnsafe() // passed not in order.. (faster then par ordered)

  val mergeParallel = element.delayOnNext(100 milliseconds).mergeMap(_ => Observable(1).delayOnNext(100 milliseconds))
  val start = System.currentTimeMillis()
  mergeParallel.completedL.runSyncUnsafe()
  println(System.currentTimeMillis() - start) // ~ 1 second

  val sequential = element.delayOnNext(100 milliseconds).flatMap(_ => Observable(1).delayOnNext(100 milliseconds))
  val startSequential = System.currentTimeMillis()
  sequential.completedL.runSyncUnsafe()
  println(System.currentTimeMillis() - startSequential) // ~ 2 second

/** if you want to back-pressure a source Observable when emitting new events, use:
  *
  * map for pure, synchronous functions which return only one value mapEval or mapParallel for effectful, possibly
  * asynchronous functions which return up to one value flatMap for effectful, possibly asynchronous functions which
  * return a stream of values If you want to process source Observables concurrently, use:
  *
  * mergeMap if you want to process all inner streams switchMap if you want to keep only the most recent inner stream
  */
