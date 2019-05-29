package com.redhat.che.start_workspace_reporter.model;

import java.util.List;

public class SlackPostAttachment {

    private String fallback;
    private String color;
    private List<SlackPostAttachmentField> fields;

    public SlackPostAttachment() {
        this.fallback = "This is a default fallback message";
        this.color = null;
        this.fields = null;
    }

    public SlackPostAttachment(String fallback, String color, List<SlackPostAttachmentField> fields) {
        this.fallback = fallback;
        this.color = color;
        this.fields = fields;
    }

    public String getFallback() {
        return fallback;
    }

    public void setFallback(String fallback) {
        this.fallback = fallback;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public List<SlackPostAttachmentField> getFields() {
        return fields;
    }

    public void setFields(List<SlackPostAttachmentField> fields) {
        this.fields = fields;
    }

    @Override
    public String toString() {
        String fields = "";
        if (this.fields != null) {
            StringBuilder builder = new StringBuilder();
            builder.append("[");
            if (this.fields.size() > 0) {
                for (SlackPostAttachmentField f : this.fields) {
                    builder.append(f.toString());
                    builder.append(",");
                }
                builder.deleteCharAt(builder.length() - 1);
            }
            builder.append("]");
            fields = builder.toString();
        }
        return "\"fallback\":"+this.fallback+",\"color\":"+this.color+",\"fields\":"+fields+"}";
    }
}
