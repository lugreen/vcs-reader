package vcsreader.vcs.svn;

import vcsreader.Change;
import vcsreader.Commit;
import vcsreader.lang.Described;
import vcsreader.lang.FunctionExecutor;
import vcsreader.vcs.infrastructure.ShellCommand;

import java.text.SimpleDateFormat;
import java.util.*;

import static java.util.Arrays.asList;
import static vcsreader.VcsProject.LogResult;

class SvnLog implements FunctionExecutor.Function<LogResult>, Described {
    private final String pathToSvn;
    private final String repositoryUrl;
    private final String repositoryRoot;
    private final Date fromDate;
    private final Date toDate;
    private final boolean useMergeHistory;

    public SvnLog(String pathToSvn, String repositoryUrl, String repositoryRoot, Date fromDate, Date toDate, boolean useMergeHistory) {
        this.pathToSvn = pathToSvn;
        this.repositoryUrl = repositoryUrl;
        this.repositoryRoot = repositoryRoot;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.useMergeHistory = useMergeHistory;
    }

    @Override public LogResult execute() {
        ShellCommand command = svnLog(pathToSvn, repositoryUrl, fromDate, toDate, useMergeHistory);

        List<String> errors = command.stderr().trim().isEmpty() ? Collections.<String>emptyList() : asList(command.stderr());
        if (errors.isEmpty()) {
            List<Commit> allCommits = CommitParser.parseCommits(command.stdout());
            List<Commit> commits = transformToSubPathCommits(deleteCommitsBefore(fromDate, allCommits));
            return new LogResult(commits, errors);
        } else {
            return new LogResult(Collections.<Commit>emptyList(), errors);
        }
    }

    static ShellCommand svnLog(String pathToSvn, String repositoryUrl, Date fromDate, Date toDate, boolean useMergeHistory) {
        return createCommand(pathToSvn, repositoryUrl, fromDate, toDate, useMergeHistory).execute();
    }

    private static ShellCommand createCommand(String pathToSvn, String repositoryUrl, Date fromDate, Date toDate, boolean useMergeHistory) {
        String mergeHistory = (useMergeHistory ? "--use-merge-history" : "");
        return new ShellCommand(
                pathToSvn, "log",
                repositoryUrl,
                "-r", dateRange(fromDate, toDate),
                mergeHistory,
                "--verbose",
                "--xml"
        );
    }

    private static String dateRange(Date fromDate, Date toDate) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return "{" + dateFormat.format(fromDate) + "}:{" + dateFormat.format(toDate) + "}";
    }

    /**
     * Delete commits because "Subversion will find the most recent revision of the repository as of the date you give".
     * See http://svnbook.red-bean.com/en/1.7/svn.tour.revs.specifiers.html#svn.tour.revs.keywords
     */
    private static List<Commit> deleteCommitsBefore(Date date, List<Commit> commits) {
        Iterator<Commit> iterator = commits.iterator();
        while (iterator.hasNext()) {
            Commit commit = iterator.next();
            if (commit.commitDate.before(date)) iterator.remove();
        }
        return commits;
    }

    private List<Commit> transformToSubPathCommits(List<Commit> commits) {
        String subPath = subPathOf(repositoryUrl, repositoryRoot);
        removeChangesNotIn(subPath, commits);
        modifyFilePathsToUse(subPath, commits);
        return commits;
    }

    private static void modifyFilePathsToUse(String subPath, List<Commit> commits) {
        for (Commit commit : commits) {
            List<Change> modifiedChanges = new ArrayList<Change>();
            for (Change change : commit.changes) {
                modifiedChanges.add(new Change(
                        change.type,
                        changeFilePath(subPath, change.filePath),
                        changeFilePath(subPath, change.filePathBefore),
                        change.revision,
                        change.revisionBefore
                ));
            }
            commit.changes.clear();
            commit.changes.addAll(modifiedChanges);
        }
    }

    private static void removeChangesNotIn(String subPath, List<Commit> commits) {
        for (Commit commit : commits) {
            for (Iterator<Change> iterator = commit.changes.iterator(); iterator.hasNext(); ) {
                Change change = iterator.next();
                if (!change.filePath.startsWith(subPath) && !change.filePathBefore.startsWith(subPath)) {
                    iterator.remove();
                }
            }
        }
    }

    private static String subPathOf(String repositoryUrl, String repositoryRoot) {
        String subPath = repositoryUrl.replace(repositoryRoot, "");
        if (subPath.startsWith("/")) subPath = subPath.substring(1);
        if (!subPath.isEmpty() && !subPath.endsWith("/")) subPath += "/";
        return subPath;
    }

    private static String changeFilePath(String subPath, String filePath) {
        int i = filePath.indexOf(subPath);
        if (i != 0) return Change.noFilePath;
        else return filePath.substring(subPath.length());
    }

    @Override public String describe() {
        return createCommand(pathToSvn, repositoryUrl, fromDate, toDate, useMergeHistory).describe();
    }
}
