package VASSAL.build;


public class Gateway {

	public static Iterable<Buildable> getBuildComponents(AbstractBuildable module) {
		return module.buildComponents;
	}

}
