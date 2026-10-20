package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.Box;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.Header;

public interface IBoxFactory {
   Box newBox(Header var1);
}
