package dev.andre.modelanchor;

public final class Attachment {
    public String path = "";
    public String name = "New attachment";
    public String part = "HEAD";
    public boolean enabled = true;
    public double x = 0.0;
    public double y = 0.0;
    public double z = 0.0;
    public double rotateX = 0.0;
    public double rotateY = 0.0;
    public double rotateZ = 0.0;
    public double scale = 0.25;

    public Attachment() {
    }

    public Attachment copy() {
        Attachment copy = new Attachment();
        copy.path = path;
        copy.name = name;
        copy.part = part;
        copy.enabled = enabled;
        copy.x = x;
        copy.y = y;
        copy.z = z;
        copy.rotateX = rotateX;
        copy.rotateY = rotateY;
        copy.rotateZ = rotateZ;
        copy.scale = scale;
        return copy;
    }

    @Override
    public String toString() {
        return name == null || name.isBlank() ? path : name;
    }
}
