package retry

import cats.{Eq, Id}
import cats.kernel.laws.discipline.BoundedSemilatticeTests
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.Checkers
import org.typelevel.discipline.scalatest.Discipline

import scala.concurrent.duration.Duration

class RetryPolicyLawsSpec extends AnyFunSuite with Discipline with Checkers {

  implicit val arbRetryPolicy: Arbitrary[RetryPolicy[Id]] = Arbitrary(
    for {
      delay <- Gen.choose(0, Long.MaxValue).map(Duration.fromNanos)
      decision <- Gen.oneOf(PolicyDecision.GiveUp,
                            PolicyDecision.DelayAndRetry(delay))
    } yield RetryPolicy[Id](_ => decision)
  )

  implicit val eqForRetryPolicy: Eq[RetryPolicy[Id]] = Eq.instance {
    case (a, b) =>
      // this Eq instance is pretty dodgy, but it matches the behaviour of the Arbitrary instance above:
      // the generated policies return the same decision for any RetryStatus value
      // so we can use any arbitrary value when testing them for equality.
      a.decideNextRetry(RetryStatus.NoRetriesYet) == b.decideNextRetry(
        RetryStatus.NoRetriesYet)
  }

  checkAll("BoundedSemilattice[RetryPolicy]",
           BoundedSemilatticeTests[RetryPolicy[Id]].boundedSemilattice)

  test("followedBy associativity") {
    check(
      (p1: RetryPolicy[Id], p2: RetryPolicy[Id], p3: RetryPolicy[Id]) =>
        Eq[RetryPolicy[Id]].eqv(p1.followedBy((p2).followedBy(p3)),
                                (p1.followedBy(p2)).followedBy(p3)))
  }

  test("followedBy left identity") {
    check((p: RetryPolicy[Id]) =>
      Eq[RetryPolicy[Id]].eqv(RetryPolicy.alwaysGiveUp.followedBy(p), p))
  }

  test("followedBy right identity") {
    check((p: RetryPolicy[Id]) =>
      Eq[RetryPolicy[Id]].eqv(p.followedBy(RetryPolicy.alwaysGiveUp), p))
  }

}
