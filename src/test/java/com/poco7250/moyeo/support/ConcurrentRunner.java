package com.poco7250.moyeo.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 여러 작업을 각자 스레드에서 같은 순간에 출발시키고 결과·예외를 모아 돌려주는 동시성 테스트 헬퍼.
 * 작업이 던진 예외는 실패로 담기만 하고, 테스트 쪽에서 어떤 예외였는지 검증한다.
 */
public final class ConcurrentRunner {

	private static final long TIMEOUT_SECONDS = 30;

	private ConcurrentRunner() {
	}

	/**
	 * 작업 하나의 결과. 성공하면 value, 실패하면 failure가 채워진다.
	 */
	public record Outcome<T>(T value, Throwable failure) {

		/** 예외 없이 끝났는지 */
		public boolean succeeded() {
			return failure == null;
		}
	}

	/**
	 * 같은 작업을 n개 동시에 실행한다.
	 */
	public static <T> List<Outcome<T>> runTogether(int n, Callable<T> task) {
		return runTogether(Collections.nCopies(n, task));
	}

	/**
	 * 작업마다 스레드를 하나씩 띄워 모두 준비되면 한꺼번에 출발시키고, 입력 순서대로 결과를 돌려준다.
	 *
	 * @throws IllegalStateException 30초 안에 끝나지 않은 작업이 있으면
	 */
	public static <T> List<Outcome<T>> runTogether(List<Callable<T>> tasks) {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> task : tasks) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return task.call();
				}));
			}
			awaitReady(ready);
			start.countDown();
			return futures.stream().map(ConcurrentRunner::collect).toList();
		} finally {
			executor.shutdownNow();
		}
	}

	private static void awaitReady(CountDownLatch ready) {
		try {
			if (!ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				throw new IllegalStateException("동시 작업 스레드가 준비되지 않았습니다");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private static <T> Outcome<T> collect(Future<T> future) {
		try {
			return new Outcome<>(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), null);
		} catch (ExecutionException e) {
			return new Outcome<>(null, e.getCause());
		} catch (TimeoutException e) {
			throw new IllegalStateException("동시 작업이 제한 시간 안에 끝나지 않았습니다", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
