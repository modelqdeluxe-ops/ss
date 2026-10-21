package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.logging;

import java.util.LinkedList;
import java.util.List;

public class BufferLogSink implements LogSink {
   private List<Message> messages = new LinkedList<>();

   @Override
   public void postMessage(Message msg) {
      this.messages.add(msg);
   }

   public List<Message> getMessages() {
      return this.messages;
   }
}
