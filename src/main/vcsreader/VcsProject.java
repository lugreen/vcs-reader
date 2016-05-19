package vcsreader;

import org.jetbrains.annotations.NotNull;
import vcsreader.vcs.commandlistener.VcsCommandListener;
import vcsreader.vcs.commandlistener.VcsCommandObserver;

import java.text.SimpleDateFormat;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.sort;
import static vcsreader.lang.StringUtil.shortened;

/**
 * Represents a project as a set of {@link VcsRoot}s.
 * This class is the main entry point for reading version control history.
 */
public class VcsProject {
	private final List<VcsRoot> vcsRoots;
	private final VcsCommandObserver commandObserver;

	public VcsProject(VcsRoot... vcsRoots) {
		this(asList(vcsRoots));
	}

	public VcsProject(List<VcsRoot> vcsRoots) {
		this.vcsRoots = vcsRoots;
		this.commandObserver = new VcsCommandObserver();
		for (VcsRoot vcsRoot : vcsRoots) {
			if (vcsRoot instanceof VcsRoot.WithCommandObserver) {
				((VcsRoot.WithCommandObserver) vcsRoot).setObserver(commandObserver);
			}
		}
	}

	/**
	 * For distributes VCS clones {@link VcsRoot}s to local file system and must be called before querying commits.
	 * Does nothing for centralized VCS because commit history can be queried from server.
	 */
	public CloneResult cloneToLocal() {
		Aggregator<CloneResult> aggregator = new Aggregator<CloneResult>(new CloneResult());
		for (VcsRoot vcsRoot : vcsRoots) {
			try {

				CloneResult cloneResult = vcsRoot.cloneToLocal();

				aggregator.aggregate(cloneResult);
			} catch (Exception e) {
				aggregator.aggregate(new CloneResult(e));
			}
		}
		return aggregator.result;
	}

	/**
	 * For distributes VCS pulls updates from upstream for all {@link VcsRoot}s
	 * Does nothing for centralized VCS because commit history can be queried from server.
	 */
	public UpdateResult update() {
		Aggregator<UpdateResult> aggregator = new Aggregator<UpdateResult>(new UpdateResult());
		for (VcsRoot vcsRoot : vcsRoots) {
			try {

				UpdateResult updateResult = vcsRoot.update();

				aggregator.aggregate(updateResult);
			} catch (Exception e) {
				aggregator.aggregate(new UpdateResult(e));
			}
		}
		return aggregator.result;
	}

	/**
	 * Request commits from VCS for all {@link VcsRoot}s within specified date range.
	 * For distributed VCS commits are read from currently checked out branch.
	 * <p>
	 * Merge commits are not logged, the commit which was merged into branch is logged instead.
	 * Therefore, it's possible to see commit with time outside of requested time range.
	 *
	 * @param from the date and time from which to request commits (inclusive, one second resolution)
	 * @param to   the date and time until which to request commits (exclusive, one second resolution)
	 */
	public LogResult log(Date from, Date to) {
		if (to.getTime() < from.getTime()) {
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
			throw new IllegalArgumentException("Invalid date range. " +
					"From: " + dateFormat.format(from) + ", to: " + dateFormat.format(to) + ".");
		}

		Aggregator<LogResult> aggregator = new Aggregator<LogResult>(new LogResult());
		for (VcsRoot vcsRoot : vcsRoots) {
			try {

				LogResult logResult = vcsRoot.log(from, to);
				logResult = (logResult != null ? logResult.setVcsRoot(vcsRoot) : null);

				aggregator.aggregate(logResult);
			} catch (Exception e) {
				aggregator.aggregate(new LogResult(e));
			}
		}
		return aggregator.result;
	}

	public VcsProject addListener(VcsCommandListener listener) {
		commandObserver.add(listener);
		return this;
	}

	public VcsProject removeListener(VcsCommandListener listener) {
		commandObserver.remove(listener);
		return this;
	}

	public List<VcsRoot> vcsRoots() {
		return Collections.unmodifiableList(vcsRoots);
	}

	@Override public String toString() {
		return "VcsProject{" + vcsRoots + '}';
	}


	private interface Aggregatable<T extends Aggregatable<T>> {
		T aggregateWith(T result);
	}


	private static class Aggregator<T extends Aggregatable<T>> {
		private T result;

		public Aggregator(T result) {
			this.result = result;
		}

		public void aggregate(T result) {
			if (this.result == null) {
				this.result = result;
			} else {
				this.result = this.result.aggregateWith(result);
			}
		}
	}


	public static class LogFileContentResult {
		private final String text;
		private final String stderr;
		private final int exitCode;

		public LogFileContentResult(@NotNull String text) {
			this(text, "", 0);
		}

		public LogFileContentResult(@NotNull String stderr, int exitCode) {
			this("", stderr, exitCode);
		}

		private LogFileContentResult(@NotNull String text, @NotNull String stderr, int exitCode) {
			this.text = text;
			this.stderr = stderr;
			this.exitCode = exitCode;
		}

		@NotNull public String text() {
			return text;
		}

		public boolean isSuccessful() {
			return stderr.isEmpty() && exitCode == 0;
		}

		@Override public String toString() {
			return "LogFileContentResult{" +
					"text='" + shortened(text, 100) + '\'' +
					", stderr='" + shortened(stderr, 100) + '\'' +
					", exitCode=" + exitCode +
					'}';
		}

		@Override public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			LogFileContentResult that = (LogFileContentResult) o;

			return exitCode == that.exitCode
				&& text.equals(that.text)
				&& stderr.equals(that.stderr);
		}

		@Override public int hashCode() {
			int result = text.hashCode();
			result = 31 * result + stderr.hashCode();
			result = 31 * result + exitCode;
			return result;
		}
	}


	public static class LogResult implements Aggregatable<LogResult> {
		private final List<VcsCommit> commits;
		private final List<String> vcsErrors;
		private final List<Exception> exceptions;

		public LogResult() {
			this(new ArrayList<VcsCommit>(), new ArrayList<String>(), new ArrayList<Exception>());
		}

		public LogResult(Exception e) {
			this(new ArrayList<VcsCommit>(), new ArrayList<String>(), asList(e));
		}

		public LogResult(List<VcsCommit> commits, List<String> vcsErrors) {
			this(commits, vcsErrors, new ArrayList<Exception>());
		}

		public LogResult(List<VcsCommit> commits, List<String> vcsErrors, List<Exception> exceptions) {
			this.commits = commits;
			this.vcsErrors = vcsErrors;
			this.exceptions = exceptions;
		}

		public LogResult aggregateWith(LogResult result) {
			List<VcsCommit> newCommits = new ArrayList<VcsCommit>(commits);
			List<String> newErrors = new ArrayList<String>(vcsErrors);
			List<Exception> newExceptions = new ArrayList<Exception>(exceptions);
			newCommits.addAll(result.commits);
			newErrors.addAll(result.vcsErrors);
			newExceptions.addAll(result.exceptions);
			sort(newCommits, new Comparator<VcsCommit>() {
				@Override public int compare(@NotNull VcsCommit commit1, @NotNull VcsCommit commit2) {
					return new Long(commit1.getTime().getTime()).compareTo(commit2.getTime().getTime());
				}
			});

			return new LogResult(newCommits, newErrors, newExceptions);
		}

		public boolean isSuccessful() {
			return vcsErrors.isEmpty() && exceptions.isEmpty();
		}

		public List<String> vcsErrors() {
			return vcsErrors;
		}

		public List<Exception> exceptions() {
			return exceptions;
		}

		public List<VcsCommit> commits() {
			return commits;
		}

		public LogResult setVcsRoot(VcsRoot vcsRoot) {
			for (VcsCommit commit : commits) {
				if (commit instanceof VcsCommit.WithRootReference) {
					((VcsCommit.WithRootReference) commit).setVcsRoot(vcsRoot);
				}
			}
			return this;
		}

		@Override public String toString() {
			return "LogResult{" +
					"commits=" + commits.size() +
					", vcsErrors=" + vcsErrors.size() +
					", exceptions=" + exceptions.size() +
					'}';
		}

		@Override public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			LogResult logResult = (LogResult) o;

			return commits.equals(logResult.commits)
				&& vcsErrors.equals(logResult.vcsErrors)
				&& exceptions.equals(logResult.exceptions);
		}

		@Override public int hashCode() {
			int result = commits.hashCode();
			result = 31 * result + vcsErrors.hashCode();
			result = 31 * result + exceptions.hashCode();
			return result;
		}
	}


	public static class UpdateResult implements Aggregatable<UpdateResult> {
		private final List<String> vcsErrors;
		private final List<Exception> exceptions;

		public UpdateResult() {
			this(new ArrayList<String>(), new ArrayList<Exception>());
		}

		public UpdateResult(Exception e) {
			this(new ArrayList<String>(), asList(e));
		}

		public UpdateResult(List<String> vcsErrors, List<Exception> exceptions) {
			this.vcsErrors = vcsErrors;
			this.exceptions = exceptions;
		}

		public UpdateResult(String error) {
			this(asList(error), new ArrayList<Exception>());
		}

		@Override public UpdateResult aggregateWith(UpdateResult result) {
			List<String> newErrors = new ArrayList<String>(vcsErrors);
			List<Exception> newExceptions = new ArrayList<Exception>(exceptions);
			newErrors.addAll(result.vcsErrors);
			newExceptions.addAll(result.exceptions);
			return new UpdateResult(newErrors, newExceptions);
		}

		public List<String> vcsErrors() {
			return vcsErrors;
		}

		public List<Exception> exceptions() {
			return exceptions;
		}

		public boolean isSuccessful() {
			return vcsErrors.isEmpty() && exceptions.isEmpty();
		}

		@Override public String toString() {
			return "UpdateResult{" +
					"vcsErrors=" + vcsErrors.size() +
					", exceptions=" + exceptions.size() +
					'}';
		}

		@Override public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			UpdateResult that = (UpdateResult) o;

			return vcsErrors.equals(that.vcsErrors) && exceptions.equals(that.exceptions);
		}

		@Override public int hashCode() {
			int result = vcsErrors.hashCode();
			result = 31 * result + exceptions.hashCode();
			return result;
		}
	}


	public static class CloneResult implements Aggregatable<CloneResult> {
		private final List<String> vcsErrors;
		private final List<Exception> exceptions;

		public CloneResult() {
			this(new ArrayList<String>(), new ArrayList<Exception>());
		}

		public CloneResult(Exception e) {
			this(new ArrayList<String>(), asList(e));
		}

		public CloneResult(List<String> vcsErrors, List<Exception> exceptions) {
			this.vcsErrors = vcsErrors;
			this.exceptions = exceptions;
		}

		public CloneResult(List<String> vcsErrors) {
			this(vcsErrors, new ArrayList<Exception>());
		}

		@Override public CloneResult aggregateWith(CloneResult result) {
			List<String> newErrors = new ArrayList<String>(vcsErrors);
			List<Exception> newExceptions = new ArrayList<Exception>(exceptions);
			newErrors.addAll(result.vcsErrors);
			newExceptions.addAll(result.exceptions);
			return new CloneResult(newErrors, newExceptions);
		}

		public List<String> vcsErrors() {
			return vcsErrors;
		}

		public List<Exception> exceptions() {
			return exceptions;
		}

		public boolean isSuccessful() {
			return vcsErrors.isEmpty() && exceptions.isEmpty();
		}

		@Override public String toString() {
			return "CloneResult{" +
					"vcsErrors=" + vcsErrors.size() +
					", exceptions=" + exceptions.size() +
					'}';
		}

		@Override public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			CloneResult that = (CloneResult) o;

			return vcsErrors.equals(that.vcsErrors) && exceptions.equals(that.exceptions);
		}

		@Override public int hashCode() {
			int result = vcsErrors.hashCode();
			result = 31 * result + exceptions.hashCode();
			return result;
		}
	}
}
