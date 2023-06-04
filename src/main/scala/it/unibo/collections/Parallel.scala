package it.unibo.collections

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import concurrent.duration.DurationLong
import scala.collection.parallel.CollectionConverters.*
import scala.collection.parallel.immutable.ParVector // Needed for scala > 2.12
def measure[L](any: => L): Long =
  val before = System.nanoTime()
  val result = any
  // Api to express time
  val duration = (System.nanoTime() - before) nanos

  (duration.toMillis)
@main def parallelPlain: Unit =
  val bigList = (0 to 1e7.toInt) map (_ + 1)
  val time = measure(bigList.map(_ * 2).fold(0)(_ + _))
  val parallel = bigList.par
  val timePar = measure(parallel.map(_ * 2).fold(0)(_ + _))
  println(s"Sequential time = $time")
  println(s"Parallel time = $timePar")
  println(s"gain : ${time / timePar.toDouble}")

// A way to express parallel computation, very concisely
@main def parallelComputation: Unit =
  val computation = ParVector(1 to 10: _*)
  computation.foreach(i => println(s"Run on ${Thread.currentThread().getName}, process = $i"))

@main def lazyComputation: Unit =
  // Pay Attention!! par work on "closed" stream, if they are infinite, par does not work!
  LazyList.continually(math.random()).foreach(println)
//LazyList.continually(math.random()).par.foreach(println(_))

/** While the parallel collections abstraction feels very much the same as normal sequential collections, it’s important
  * to note that its semantics differs, especially with regards to side-effects and non-associative operations.
  */
@main def reduceProblems: Unit =
  println((1 to 100).fold(0)(_ - _))
  println((1 to 100).par.fold(0)(_ - _)) // KO !!!
  var counter = 0
  (1 to 100) foreach { _ => counter += 1 }
  assert(counter == 100) // OK!!
  (1 to 100).par.foreach(_ => counter += 1)
  assert(counter == 200) // KO!!!
