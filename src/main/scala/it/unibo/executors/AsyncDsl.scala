package it.unibo.executors

import java.util.concurrent.{CountDownLatch, Executors}
import scala.concurrent.{ExecutionContext, blocking}

// In scala, a natural way to "enrich" the language consist in using contexts + entrypoints. async is the entrypoint of the dsl, ExecutionContext is the context
def async(any: => Unit)(using ExecutionContext): Unit =
  summon[ExecutionContext].execute(() => any)

@main def tryDsl(): Unit =
  given ExecutionContext = ExecutionContext.global // express the context ==> enrich the language
  println("Do somethings")
  async: // I can use async like a new construct
      println("order??")
  println("After")

@main def orderExecution: Unit =
  val bigNumber = 20
  val latch = CountDownLatch(20)
  println("Hello!!")
  given ExecutionContext =
    ExecutionContext.global // ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  (1 to bigNumber) foreach: i =>
    async:
        blocking:
            latch.countDown()
            println(s"I am in: ${Thread.currentThread().getName} -- " + i)
            latch.await()
  // Block!!
  async(latch.countDown())
  latch.await()
  println("Over..")
