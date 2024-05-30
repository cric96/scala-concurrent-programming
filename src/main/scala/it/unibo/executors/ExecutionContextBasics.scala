package it.unibo.executors
import java.util.concurrent.Executors
import scala.concurrent.* // set of operations to enable concurrency

def sayGreet()(using context: ExecutionContext): Unit =
  context.execute(() => println("run!"))

@main def executeTask: Unit =
  /** Use global when: i) you do not care about where the computation is executed, ii) the operations are not blocking
    */
  val context = ExecutionContext.global // global execution context
  context.execute(() => println("Task done!"))
  val fromExecutors = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
  fromExecutors.execute(() => println("Java wrapper"))
  // sayGreet() // error
  given ExecutionContext = fromExecutors // enrich the context
  sayGreet()
