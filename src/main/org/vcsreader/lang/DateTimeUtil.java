package org.vcsreader.lang;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static java.util.Arrays.asList;

public class DateTimeUtil {
	public static final TimeZone UTC = TimeZone.getTimeZone("UTC");

	public static Instant date(String s) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("kk:mm:ss dd/MM/yyyy").withZone(ZoneOffset.UTC);
		return formatter.parse("00:00:00 " + s, Instant::from);
	}

	public static TimeRange timeRange(Date from, Date to) {
		return new TimeRange(from, to);
	}

	public static TimeRange timeRange(String from, String to) {
		return new TimeRange(date(from), date(to));
	}

	public static Date dateTime(String s) {
		List<DateFormat> formats = asList(
				dateTimeFormat("kk:mm dd/MM/yyyy", UTC),
				dateTimeFormat("kk:mm:ss dd/MM/yyyy", UTC),
				dateTimeFormat("kk:mm:ss.SSS dd/MM/yyyy", UTC),
				dateTimeFormat("MMM dd kk:mm:ss yyyy Z", UTC),
				dateTimeFormat("E MMM dd kk:mm:ss Z yyyy", UTC)
		);
		for (DateFormat format : formats) {
			try {
				return format.parse(s);
			} catch (ParseException ignored) {
			}
		}
		throw new RuntimeException("Failed to parse string as dateTime: " + s);
	}

	public static DateFormat dateTimeFormat(String pattern, TimeZone timeZone) {
		SimpleDateFormat format = new SimpleDateFormat(pattern);
		format.setTimeZone(timeZone);
		return format;
	}
}
