package app.organicmaps.maplayer;

import app.organicmaps.sdk.MapMode;
import app.organicmaps.sdk.maplayer.Mode;
import java.util.ArrayList;
import java.util.List;

public class LayersUtils
{
  public static List<Mode> getAvailableLayers()
  {
    List<Mode> availableLayers = new ArrayList<>();
    availableLayers.add(Mode.OUTDOORS);
    availableLayers.add(Mode.ISOLINES);
    // Transit lines belong to the Public transport map mode.
    if (MapMode.get() == MapMode.PublicTransport)
      availableLayers.add(Mode.SUBWAY);
    availableLayers.add(Mode.BUILDINGS_3D);
    availableLayers.add(Mode.NIGHT);
    return availableLayers;
  }
}
