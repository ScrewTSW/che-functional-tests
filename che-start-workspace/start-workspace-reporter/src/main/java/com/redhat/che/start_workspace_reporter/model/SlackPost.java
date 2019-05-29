package com.redhat.che.start_workspace_reporter.model;

import java.util.List;

public class SlackPost {

    private String channel;
    private List<SlackPostAttachment> attachments;

    public SlackPost() {
        this.channel = "@tdancs";
        this.attachments = null;
    }

    public SlackPost(String channel, List<SlackPostAttachment> attachments) {
        this.channel = channel;
        this.attachments = attachments;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public List<SlackPostAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<SlackPostAttachment> attachments) {
        this.attachments = attachments;
    }
}
