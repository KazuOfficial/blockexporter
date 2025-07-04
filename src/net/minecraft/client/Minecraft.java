import net.minecraft.src.ModBlockExporter;

public abstract class Minecraft {
  // ...
	public void runTick() {
		ModBlockExporter.onTick(this);
    // ...
	}
}
