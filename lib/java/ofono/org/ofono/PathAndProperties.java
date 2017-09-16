package org.ofono;

import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.Variant;

import java.util.Map;

public final class PathAndProperties extends Struct {
    @Position(0)
    public final Path path;
    @Position(1)
    public final Map<String, Variant> props;

    public PathAndProperties(Path path, Map<String, Variant> props) {
        this.path = path;
        this.props = props;
    }
}
