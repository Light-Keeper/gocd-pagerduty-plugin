package com.pagerduty.go.notification.pagerduty;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Iso8601DateAdapter implements JsonSerializer<Date>, JsonDeserializer<Date> {

    private final DateFormat format;

    public Iso8601DateAdapter() {
        format = new SimpleDateFormat("yyyy-mm-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public synchronized JsonElement serialize(Date date, Type type, JsonSerializationContext context) {
        if (date == null) {
            return JsonNull.INSTANCE;
        } else {
            return new JsonPrimitive(format.format(date));
        }
    }

    @Override
    public synchronized Date deserialize(JsonElement element, Type type, JsonDeserializationContext context) {
        if ("".equals(element.getAsString())) {
            return null;
        }

        try {
            return format.parse(element.getAsString());
        } catch (ParseException e) {
            throw new JsonParseException(e);
        }
    }


}
