package bid.yuanlu.mcwarehouse.model.config;

import java.util.List;

public class PathfinderConfig {

	public String type;
	public boolean allowFlight;
	public boolean allowPortal;
	public List<String> warpCommands;
	public List<RouteConfig> preferredRoutes;

	public static class RouteConfig {
		public String from;
		public String to;
		public String path;
	}
}
