package it.unibo.async
import java.awt.image.BufferedImage
import java.io.File
import java.net.{URI, URL}
import java.nio.file.{Files, Paths}
import java.util.concurrent.{CountDownLatch, Executors}
import javax.imageio.ImageIO
import scala.concurrent.{Await, ExecutionContext, Future, blocking}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.io.Source
import scala.util.{Failure, Success} // Another way to put in context the execution context

/** Future expresses a concurrent computation. It needs an execution context in order to be executed Futures are eager,
  * i.e., they start immediately after they creation
  *
  * Futures are monads, so it has the same interface of Option, Try, ..., but in this case the Monad wrap any kind of
  * async computation
  */
@main def createFuture: Unit =
  Future: // it starts when the future is created..
    println(s"Executed in: ${Thread.currentThread().getName}")
    10
  Thread.sleep(500)

@main def callback: Unit =
  Future(10)
    .onComplete:
      case Success(value) => println(s"Hurray! $value")
      case Failure(exception) => println("Oh no..")

  Thread.sleep(500)

/** I can update value computed concurrently using map & flatMap & filter */
@main def futureManipulation: Unit =
  val build = Future(Source.fromFile("build.sbt")) // Concurrent program expressed as sequence of map
    .map(source => source.getLines())
    .map(lines => lines.mkString("\n"))

  val scalafmt = Future(Source.fromFile(".scalafmt.conf"))
    .map(source => source.getLines())
    .map(lines => lines.mkString("\n"))

  // NB! scalafmt and build are concurrent here..
  val combine = build.flatMap(data => scalafmt.map(other => data + other)) // Concurrent Program can be composed

  combine.onComplete: // To handle the result, you can use "on complete"
    case Success(value) => println(s"Files = $value")
    case Failure(exception) => println("Ops...")

  Await.result(combine, Duration.Inf) // You should not wait the result.. Typically this is done at the end of the main

/** Since Future are monad, you can manipulate the data with for-compression */
@main def futureManipulationApi: Unit =
  def extractLines(source: Source): String = source.getLines().mkString("\n")
  def readFile(name: String): Future[Source] =
    val result = Future:
      Thread.sleep(500)
      Source.fromFile(name)
    result.onComplete(_ => println(name))
    result

  val concurrentManipulation = for
    buildSbt <- readFile("build.sbt")
    scalafmt <- readFile(".scalafmt.conf") // NB! build this two future are sequential
    fileSbt = extractLines(buildSbt) // I can map the "lazy" data inside a Future "for-comprehension"..
    fileFmt = extractLines(scalafmt)
  yield fileFmt + fileSbt

  println(Await.result(concurrentManipulation, Duration.Inf))

  // Concurrent
  val buildSbtFuture = readFile("build.sbt")
  val scalafmtFuture = readFile(".scalafmt.conf")
  for
    buildSbt <- buildSbtFuture
    scalafmt <- scalafmtFuture // NB! build this two future are sequential
    fileSbt = extractLines(buildSbt) // I can you the data inside a Future manipulation..
    fileFmt = extractLines(scalafmt)
  yield fileFmt + fileSbt

  Thread.sleep(1000)

def getImage(url: String): Future[BufferedImage] = Future(ImageIO.read(URI.create(url).toURL))
def storeImage(name: String, image: BufferedImage): Future[Unit] = Future(ImageIO.write(image, "jpg", File(name)))

// Future can be used for IO management (in general to manage data flow)
@main def ioManagement: Unit =
  val result = for
    person <- getImage("https://thispersondoesnotexist.com/")
    _ <- storeImage("person.jpg", person)
  yield ()
  Await.result(result, Duration.Inf)

// You can pass your context explicitly, using the context
@main def usingContext: Unit =
  val myContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
  def createFuture(using ExecutionContext) = Future {
    println(s"Hey! The thread is: ${Thread.currentThread().getName}")
  }
  createFuture(using global) // I can pass explicitly the context (i.e. when I want a fine control for execution)
  given ExecutionContext = myContext // I can overwrite the execution context
  createFuture

// Something happen that you have a sequence of Future and you want way the end of all of them.
// In this case, You have to convert List[Future[_]] into a Future[List[_]]...
@main def sequence: Unit =
  Files.createDirectories(Paths.get("./people"))
  val requests = (0 to 10)
    .map(i => (i, getImage("https://thispersondoesnotexist.com/")))
    .map { case (i, people) => people.flatMap(storeImage(s"./people/$i.jpg", _)) }
  val allImages = Future.sequence(requests)
  Await.result(allImages, Duration.Inf)

// Concurrent computation could fail...
@main def exception: Unit =
  val failed = getImage("foo")
  failed.onComplete { case Failure(exception) =>
    println("error..")
  }
  Await.ready(failed, Duration.Inf)
  val fail = Future(1).failed // create a future that completes if the future fails
  fail.foreach(println)
  Await.ready(fail, Duration.Inf)
  val empty = Future(1).filter(_ => false)
  empty.failed.foreach(println) // failed
  Await.ready(empty, Duration.Inf)

//... With future you can recover the failed computation
@main def recover: Unit =
  val fail = Future("hello").filter(_ == "ciao").recoverWith(_ => Future("ciao"))
  fail.foreach(println) // You can consume the future using foreach
  Thread.sleep(500)

@main def blockingExample: Unit =
  // given ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(8))
  val blocks = 20
  val latch = CountDownLatch(blocks)
  val all = (1 to blocks).map(i =>
    Future {
      println(s"Turn $i")
      blocking {
        latch.countDown()
        latch.await()
      }
    }
  )

  Await.ready(Future.sequence(all), Duration.Inf)

// When a future is completed, each "consumer" received the same value => it memoizes the results!
@main def futureMemoized: Unit =
  val random = Future(math.random())
  val another = Future(1).flatMap(_ => random).foreach(println)
  random.foreach(println)
  Thread.sleep(100)
  random.foreach(println)
  Thread.sleep(100)
