package org.ofono;
import java.util.List;
import org.freedesktop.dbus.DBusInterface;
public interface AllowedAccessPoints extends DBusInterface
{

  public List<String> GetAllowedAccessPoints();

}
