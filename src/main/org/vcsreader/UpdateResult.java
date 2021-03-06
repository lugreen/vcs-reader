package org.vcsreader;

import org.vcsreader.lang.Aggregatable;
import org.vcsreader.vcs.VcsCommand.ExceptionWrapper;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

public class UpdateResult implements Aggregatable<UpdateResult> {
	public static final ExceptionWrapper<UpdateResult> adapter = UpdateResult::new;
	private final List<Exception> exceptions;


	public UpdateResult() {
		this(new ArrayList<>());
	}

	public UpdateResult(Exception e) {
		this(asList(e));
	}

	public UpdateResult(List<Exception> exceptions) {
		this.exceptions = exceptions;
	}

	@Override public UpdateResult aggregateWith(UpdateResult value) {
		List<Exception> newExceptions = new ArrayList<>(exceptions);
		newExceptions.addAll(value.exceptions);
		return new UpdateResult(newExceptions);
	}

	public List<Exception> exceptions() {
		return exceptions;
	}

	public boolean isSuccessful() {
		return exceptions.isEmpty();
	}

	@Override public String toString() {
		return "UpdateResult{exceptions=" + exceptions.size() + '}';
	}

	@Override public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		UpdateResult that = (UpdateResult) o;

		return exceptions != null ? exceptions.equals(that.exceptions) : that.exceptions == null;
	}

	@Override public int hashCode() {
		return exceptions != null ? exceptions.hashCode() : 0;
	}
}
