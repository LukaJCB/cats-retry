package retry

import cats.Applicative
import cats.kernel.BoundedSemilattice
import retry.PolicyDecision._

import scala.concurrent.duration.Duration
import cats.Apply

case class RetryPolicy[M[_]](
    decideNextRetry: RetryStatus => M[PolicyDecision]) {
  def join(rp: RetryPolicy[M])(implicit M: Apply[M]): RetryPolicy[M] =
    RetryPolicy[M](status =>
      M.map2(decideNextRetry(status), rp.decideNextRetry(status)) {
        case (DelayAndRetry(a), DelayAndRetry(b)) => DelayAndRetry(a max b)
        case _                                    => GiveUp
    })

  def meet(rp: RetryPolicy[M])(implicit M: Apply[M]): RetryPolicy[M] =
    RetryPolicy[M](status =>
      M.map2(decideNextRetry(status), rp.decideNextRetry(status)) {
        case (DelayAndRetry(a), DelayAndRetry(b)) => DelayAndRetry(a min b)
        case (s @ DelayAndRetry(_), GiveUp)       => s
        case (GiveUp, s @ DelayAndRetry(_))       => s
        case _                                    => GiveUp
    })
}

object RetryPolicy {

  def lift[M[_]](
      f: RetryStatus => PolicyDecision
  )(
      implicit
      M: Applicative[M]
  ): RetryPolicy[M] =
    RetryPolicy[M](decideNextRetry = retryStatus => M.pure(f(retryStatus)))

  implicit def boundedSemilatticeForRetryPolicy[M[_]](
      implicit M: Applicative[M]
  ): BoundedSemilattice[RetryPolicy[M]] =
    new BoundedSemilattice[RetryPolicy[M]] {

      override def empty: RetryPolicy[M] =
        RetryPolicies.constantDelay[M](Duration.Zero)

      override def combine(
          x: RetryPolicy[M],
          y: RetryPolicy[M]
      ): RetryPolicy[M] = x.join(y)
    }

}
