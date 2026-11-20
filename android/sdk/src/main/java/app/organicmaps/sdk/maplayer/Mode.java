package app.organicmaps.sdk.maplayer;

import android.content.Context;
import androidx.annotation.NonNull;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.MapMode;
import app.organicmaps.sdk.maplayer.isolines.IsolinesManager;
import app.organicmaps.sdk.maplayer.subway.SubwayManager;
import app.organicmaps.sdk.maplayer.traffic.TrafficManager;
import app.organicmaps.sdk.util.Config;

public enum Mode
{
  TRAFFIC {
    @Override
    public boolean isEnabled(@NonNull Context context)
    {
      return !SubwayManager.isEnabled() && TrafficManager.INSTANCE.isEnabled();
    }

    @Override
    public void setEnabled(@NonNull Context context, boolean isEnabled)
    {
      TrafficManager.INSTANCE.setEnabled(isEnabled);
    }
  },
  SUBWAY {
    @Override
    public boolean isEnabled(@NonNull Context context)
    {
      return SubwayManager.isEnabled();
    }

    @Override
    public void setEnabled(@NonNull Context context, boolean isEnabled)
    {
      SubwayManager.setEnabled(isEnabled);
    }
  },

  ISOLINES {
    @Override
    public boolean isEnabled(@NonNull Context context)
    {
      return IsolinesManager.isEnabled();
    }

    @Override
    public void setEnabled(@NonNull Context context, boolean isEnabled)
    {
      IsolinesManager.setEnabled(isEnabled);
    }
  },
  OUTDOORS {
    @Override
    public boolean isEnabled(@NonNull Context context)
    {
      return Framework.nativeIsOutdoorsLayerEnabled();
    }

    @Override
    public void setEnabled(@NonNull Context context, boolean isEnabled)
    {
      Framework.nativeSetOutdoorsLayerEnabled(isEnabled);
      // TODO: ThemeSwitcher is outside sdk package. Properly fix dependencies
      // ThemeSwitcher.INSTANCE.restart(true);
    }
  },
  BUILDINGS_3D {
    @Override
    public boolean isEnabled(@NonNull Context context)
    {
      return MapMode.isBuildings3dEnabled();
    }

    @Override
    public void setEnabled(@NonNull Context context, boolean isEnabled)
    {
      MapMode.setBuildings3dEnabled(isEnabled);
    }
  },
  /// The app must restart its theme after a change.
  NIGHT {
    @Override
    public boolean isEnabled(@NonNull Context context)
    {
      return Config.UiTheme.isNight(Config.UiTheme.getCurrent());
    }

    @Override
    public void setEnabled(@NonNull Context context, boolean isEnabled)
    {
      Config.UiTheme.setUiThemeSettings(isEnabled ? Config.UiTheme.NIGHT : Config.UiTheme.DEFAULT);
    }
  };

  public abstract boolean isEnabled(@NonNull Context context);

  public abstract void setEnabled(@NonNull Context context, boolean isEnabled);
}
