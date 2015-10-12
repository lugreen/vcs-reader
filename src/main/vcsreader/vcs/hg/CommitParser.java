package vcsreader.vcs.hg;

import vcsreader.Change;
import vcsreader.Commit;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static vcsreader.Change.Type.*;
import static vcsreader.lang.StringUtil.split;

class CommitParser {
    public static final String commitStartSeparatorFormat = "\\x15\\x16\\x17\\x18\\x19";
    public static final String commitFieldSeparatorFormat = "\\x19\\x18\\x17\\x16\\x15";
    public static final String fileSeparatorFormat = "\\x17\\x16\\x15\\x19\\x18";
    private static final String commitStartSeparator = "\u0015\u0016\u0017\u0018\u0019";
    private static final String commitFieldsSeparator = "\u0019\u0018\u0017\u0016\u0015";
    private static final String fileSeparator = "\u0017\u0016\u0015\u0019\u0018";
    private static final String hgNoRevision = "0000000000000000000000000000000000000000";

    public static List<Commit> parseListOfCommits(String stdout) {
        ArrayList<Commit> commits = new ArrayList<Commit>();
        List<String> commitsAsString = split(stdout, commitStartSeparator);

        for (String s : commitsAsString) {
            commits.add(parseCommit(s, commitFieldsSeparator));
        }
        return commits;
    }

    private static Commit parseCommit(String s, String fieldsSeparator) {
        List<String> values = split(s, fieldsSeparator);

        String revision = values.get(0);
        String revisionBefore = values.get(1);
        if (revisionBefore.equals(hgNoRevision)) {
            revisionBefore = Change.noRevision;
        }
        Date commitDate = parseDate(values.get(2));
        String author = values.get(3);
        String comment = values.get(4);

        List<Change> filesAdded = new ArrayList<Change>();
        for (String filePath : values.get(5).split(fileSeparator)) {
            if (filePath.isEmpty()) continue;
            filesAdded.add(new Change(NEW, filePath, revision));
        }
        List<Change> filesDeleted = new ArrayList<Change>();
        for (String filePath : values.get(6).split(fileSeparator)) {
            if (filePath.isEmpty()) continue;
            filesDeleted.add(new Change(DELETED, filePath, revision));
        }
        List<Change> filesMoved = new ArrayList<Change>();
        for (String filePath : values.get(7).split(fileSeparator)) {
            if (filePath.isEmpty()) continue;
            String filePathBefore = ""; // TODO
            filesMoved.add(new Change(MOVED, filePath, filePathBefore, revision, revisionBefore));
        }
        List<Change> filesModified = new ArrayList<Change>();
        for (String filePath : values.get(8).split(fileSeparator)) {
            if (filePath.isEmpty()) continue;
            filesModified.add(new Change(MODIFICATION, filePath, filePath, revision, revisionBefore));
        }

        List<Change> changes = new ArrayList<Change>();
        changes.addAll(filesAdded);
        changes.addAll(filesDeleted);
        changes.addAll(filesMoved);
        changes.addAll(filesModified);

        return new Commit(revision, revisionBefore, commitDate, author, comment, changes);
    }

    private static Date parseDate(String s) {
        return new Date((long) (Double.parseDouble(s) * 1000));
    }
}