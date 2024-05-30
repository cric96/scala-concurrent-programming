package it.unibo.async

import scala.concurrent.Promise
import concurrent.ExecutionContext.Implicits.global

/** A promise and a future represent two aspects of a single–assignment variable–the promise allows you to assign a
  * value to the future object, whereas the future allows you to read that value.
  *
  * Useful to adapt callback based API to future based
  */

@main def createPromise: Unit =
  val promise = Promise[Int]() // Promise created..
  promise.future.foreach(println) // "interface" to read the value
  Thread.sleep(500)
  promise.success(10) // Everything ok!!
  Thread.sleep(1000)
  try promise.success(10) // Exception!!
  catch case ex: IllegalStateException => println("Already completed!!")

  println(promise.trySuccess(10))

@main def endWithError: Unit =
  val promise = Promise()
  promise.failure(new Exception("foo"))
  promise.future.failed.foreach(println)
  Thread.sleep(500)
