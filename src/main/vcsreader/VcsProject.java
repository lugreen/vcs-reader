package vcsreader;

import org.jetbrains.annotations.NotNull;
import vcsreader.vcs.commandlistener.VcsCommandListener;
import vcsreader.vcs.commandlistener.VcsCommandObserver;

import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.sort;

/**
 * Represents a project as a set of {@link VcsRoot}s.
 * This is the main entry point for reading version control history.
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
		Accumulator<CloneResult> accumulator = new Accumulator<CloneResult>(new CloneResult());
		for (VcsRoot vcsRoot : vcsRoots) {
			try {

				CloneResult cloneResult = vcsRoot.cloneToLocal();

				accumulator.update(cloneResult);
			} catch (Exception e) {
				accumulator.update(new CloneResult(e));
			}
		}
		return accumulator.mergedResult;
	}

	/**
	 * For distributes VCS pulls updates from upstream for all {@link VcsRoot}s
	 * Does nothing for centralized VCS because commit history can be queried from server.
	 */
	public UpdateResult update() {
		Accumulator<UpdateResult> accumulator = new Accumulator<UpdateResult>(new UpdateResult());
		for (VcsRoot vcsRoot : vcsRoots) {
			try {

				UpdateResult updateResult = vcsRoot.update();

				accumulator.update(updateResult);
			} catch (Exception e) {
				accumulator.update(new UpdateResult(e));
			}
		}
		return accumulator.mergedResult;
	}

	/**
	 * Requests commits from VCS for all {@link VcsRoot}s for specified date range.
	 * @param fromDate TODO
	 * @param toDate TODO
	 */
	public LogResult log(Date fromDate, Date toDate) {
		Accumulator<LogResult> accumulator = new Accumulator<LogResult>(new LogResult());
		for (VcsRoot vcsRoot : vcsRoots) {
			try {

				LogResult logResult = vcsRoot.log(fromDate, toDate);
				logResult = (logResult != null ? logResult.setVcsRoot(vcsRoot) : null);

				accumulator.update(logResult);
			} catch (Exception e) {
				accumulator.update(new LogResult(e));
			}
		}
		return accumulator.mergedResult;
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


    private interface Mergeable<T extends Mergeable<T>> {
        T mergeWith(T result);
    }

    private static class Accumulator<T extends Mergeable<T>> {
        private T mergedResult;

        public Accumulator(T mergedResult) {
            this.mergedResult = mergedResult;
        }

        public void update(T result) {
            if (mergedResult == null) {
                mergedResult = result;
            } else {
                mergedResult = mergedResult.mergeWith(result);
            }
        }
    }


    public static class LogContentResult {
        private final String text;
        private final String stderr;
        private final int exitCode;

        public LogContentResult(@NotNull String text) {
            this(text, "", 0);
        }

        public LogContentResult(@NotNull String stderr, int exitCode) {
            this("", stderr, exitCode);
        }

        private LogContentResult(@NotNull String text, @NotNull String stderr, int exitCode) {
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
    }

    public static class LogResult implements Mergeable<LogResult> {
        private final List<Commit> commits;
        private final List<String> vcsErrors;
        private final List<Exception> exceptions;

        public LogResult() {
            this(new ArrayList<Commit>(), new ArrayList<String>(), new ArrayList<Exception>());
        }

        public LogResult(Exception e) {
            this(new ArrayList<Commit>(), new ArrayList<String>(), asList(e));
        }

        public LogResult(List<Commit> commits, List<String> vcsErrors) {
            this(commits, vcsErrors, new ArrayList<Exception>());
        }

        public LogResult(List<Commit> commits, List<String> vcsErrors, List<Exception> exceptions) {
            this.commits = commits;
            this.vcsErrors = vcsErrors;
            this.exceptions = exceptions;
        }

        public LogResult mergeWith(LogResult result) {
            List<Commit> newCommits = new ArrayList<Commit>(commits);
            List<String> newErrors = new ArrayList<String>(vcsErrors);
            List<Exception> newExceptions = new ArrayList<Exception>(exceptions);
            newCommits.addAll(result.commits);
            newErrors.addAll(result.vcsErrors);
            newExceptions.addAll(result.exceptions);
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

        public List<Commit> commits() {
	        // TODO sort in constructor?
            List<Commit> commits = new ArrayList<Commit>(this.commits);
            sort(commits, new Comparator<Commit>() {
                @Override public int compare(@NotNull Commit commit1, @NotNull Commit commit2) {
                    return new Long(commit1.commitTime.getTime()).compareTo(commit2.commitTime.getTime());
                }
            });
            return commits;
        }

        public LogResult setVcsRoot(VcsRoot vcsRoot) {
            for (Commit commit : commits) {
                commit.setVcsRoot(vcsRoot);
            }
            return this;
        }
    }

    public static class UpdateResult implements Mergeable<UpdateResult> {
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

        @Override public UpdateResult mergeWith(UpdateResult result) {
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
    }

    public static class CloneResult implements Mergeable<CloneResult> {
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

        @Override public CloneResult mergeWith(CloneResult result) {
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
    }
}
