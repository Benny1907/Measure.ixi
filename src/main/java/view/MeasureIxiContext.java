package view;

import org.iota.ict.ixi.context.ConfigurableIxiContext;
import org.iota.ixi.model.Neighbor;
import org.iota.ixi.utils.IctRestCaller;
import org.iota.ixi.utils.UuidGenerator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MeasureIxiContext extends ConfigurableIxiContext {

    private static final Logger log = LogManager.getLogger("ReportIxiContext");

    // Property names
    private static final String ICT_REST_PORT = "Ict REST API Port";
    private static final String ICT_REST_PASSWORD = "Ict REST API Password";
    private static final String NAME = "Name";
    private static final String NEIGHBORS = "Neighbors";
    private static final String NEIGHBOR_ADDRESS = "_address";
    private static final String NEIGHBOR_PUBLIC_ADDRESS = "publicAddress";
    private static final String PUBLIC_ADDRESS = "Public address";

    // Property defaults
    private static final String DEFAULT_ICT_VERSION = "";
    private static final int DEFAULT_ICT_ROUND_DURATION = 60000;
    private static final int DEFAULT_ICT_REST_PORT = 2187;
    private static final String DEFAULT_ICT_REST_PASSWORD = "change_me_now";
    private static final JSONObject DEFAULT_CONFIGURATION = new JSONObject();
    private static final String DEFAULT_NAME = "YOUR_NAME (ict-0)";
    private static final JSONArray DEFAULT_NEIGHBORS = new JSONArray();
    private static final String DEFAULT_PUBLIC_ADDRESS = "your.public.ict.address:1337";

    // Context properties
    private String ictVersion = DEFAULT_ICT_VERSION;
    private int ictRoundDuration = DEFAULT_ICT_ROUND_DURATION;
    private int ictRestPort = DEFAULT_ICT_REST_PORT;
    private String ictRestPassword = DEFAULT_ICT_REST_PASSWORD;
    private String name = DEFAULT_NAME;
    private String publicAddress = DEFAULT_PUBLIC_ADDRESS;

    // Non-configurable properties
    private String uuid = null;
    private final List<Neighbor> neighbors = new LinkedList<>();

    static {
        DEFAULT_CONFIGURATION.put(ICT_REST_PORT, DEFAULT_ICT_REST_PORT);
        DEFAULT_CONFIGURATION.put(ICT_REST_PASSWORD, DEFAULT_ICT_REST_PASSWORD);
        DEFAULT_CONFIGURATION.put(NAME, DEFAULT_NAME);
        DEFAULT_CONFIGURATION.put(NEIGHBORS, DEFAULT_NEIGHBORS.toString());
        DEFAULT_CONFIGURATION.put(PUBLIC_ADDRESS, DEFAULT_PUBLIC_ADDRESS);
    }

    public MeasureIxiContext() {
        super(DEFAULT_CONFIGURATION);
        applyConfiguration();
    }


    @Override
    public JSONObject getConfiguration() {
        syncIctNeighbors();

        final List<JSONObject> jsonNeighbor = new LinkedList<>();
        for (Neighbor neighbor : getNeighbors()) {
            jsonNeighbor.add(new JSONObject()
                    .put(NEIGHBOR_ADDRESS, neighbor.getAddress())
                    .put(NEIGHBOR_PUBLIC_ADDRESS, neighbor.getPublicAddress()));
        }
        final JSONArray jsonNeighbors = new JSONArray(jsonNeighbor);

        final JSONObject configuration = new JSONObject()
                .put(ICT_REST_PORT, getIctRestPort())
                .put(ICT_REST_PASSWORD, getIctRestPassword())
                .put(NAME, getName())
                .put(NEIGHBORS, jsonNeighbors.toString())
                .put(PUBLIC_ADDRESS, getPublicAddress());

        return configuration;
    }

    @Override
    protected void validateConfiguration(final JSONObject newConfiguration) {
        validateName(newConfiguration);
        validateNeighbors(newConfiguration);
        validateIctRestConnectivity(newConfiguration);
        validatePublicAddress(newConfiguration);
    }

    @Override
    public void applyConfiguration() {
        setIctRestPort(configuration.getInt(ICT_REST_PORT));
        setIctRestPassword(configuration.getString(ICT_REST_PASSWORD));
        setName(configuration.getString(NAME));

        if (configuration.has(PUBLIC_ADDRESS)) {
            setPublicAddress(configuration.getString(PUBLIC_ADDRESS));
            setUuid(UuidGenerator.generate(configuration.getString(PUBLIC_ADDRESS)));
        }

        // Get new neighbor changes
        JSONArray newNeighborConfiguration = null;
        if (configuration.get(NEIGHBORS) instanceof String) {
            newNeighborConfiguration = new JSONArray(configuration.getString(NEIGHBORS));
        } else if (configuration.get(NEIGHBORS) instanceof JSONArray) {
            newNeighborConfiguration = configuration.getJSONArray(NEIGHBORS);
        } else {
            newNeighborConfiguration = new JSONArray();
        }

        // Apply new neighbor changes
        for (int i = 0; i < newNeighborConfiguration.length(); i++) {
            final JSONObject jsonNeighbor = newNeighborConfiguration.getJSONObject(i);

            final String _address = jsonNeighbor.getString(NEIGHBOR_ADDRESS);

            Neighbor neighbor = getNeighborByStaticAddress(_address);
            if (neighbor == null) {
                neighbor = new Neighbor(_address);
                neighbors.add(neighbor);
            }

            if (jsonNeighbor.has(NEIGHBOR_PUBLIC_ADDRESS)) {
                final String publicAddress = jsonNeighbor.getString(NEIGHBOR_PUBLIC_ADDRESS);
                if (!publicAddress.isEmpty()) {
                    neighbor.setPublicAddress(publicAddress);
                }
            }
        }
    }

    private void validateIctRestConnectivity(final JSONObject newConfiguration) {
        if (!newConfiguration.has(ICT_REST_PASSWORD)) {
            throw new IllegalPropertyException(ICT_REST_PASSWORD, "not defined");
        }
        if (!(newConfiguration.get(ICT_REST_PASSWORD) instanceof String)) {
            throw new IllegalPropertyException(ICT_REST_PASSWORD, "not a string");
        }
        final JSONObject response = IctRestCaller.getInfo(newConfiguration.getInt(ICT_REST_PORT), newConfiguration.getString(ICT_REST_PASSWORD));
        if (response == null) {
            throw new IllegalPropertyException("Ict REST API port/password", "Connectivity check with Ict REST API failed");
        }
    }

    private void validateName(final JSONObject newConfiguration) {
        if (!newConfiguration.has(NAME)) {
            throw new IllegalPropertyException(NAME, "not defined");
        }
        if (!(newConfiguration.get(NAME) instanceof String)) {
            throw new IllegalPropertyException(NAME, "not a string");
        }
        if (!newConfiguration.getString(NAME).matches(".+\\s\\(ict-\\d+\\)")) {
            throw new IllegalPropertyException(NAME, "please follow the naming convention: \"<name> (ict-<number>)\"");
        }
        if (newConfiguration.getString(NAME).equals(DEFAULT_NAME)) {
            throw new IllegalPropertyException(NAME,
                    String.format("please assign your personal ict name instead of '%s'", DEFAULT_NAME));
        }
    }

    private void validatePublicAddress(final JSONObject newConfiguration) {
        if (!newConfiguration.has(PUBLIC_ADDRESS)) {
            return;
        }
        if (!(newConfiguration.get(PUBLIC_ADDRESS) instanceof String)) {
            throw new IllegalPropertyException(PUBLIC_ADDRESS, "not a string");
        }
        if (newConfiguration.getString(PUBLIC_ADDRESS).isEmpty()) {
            throw new IllegalPropertyException(PUBLIC_ADDRESS, "the public ict \"address:port\" must be specified");
        }
        if (newConfiguration.getString(PUBLIC_ADDRESS).equals("your.public.ict.address:1337")) {
            throw new IllegalPropertyException(PUBLIC_ADDRESS, "set your own ict public address \"address:port\"");
        }
        if (!newConfiguration.getString(PUBLIC_ADDRESS).matches("^.{4,253}:.{1,5}")) {
            throw new IllegalPropertyException(PUBLIC_ADDRESS, "public address incorrectly formatted, expected format: \"address:port\"");
        }
    }

    private void validateNeighbors(final JSONObject newConfiguration) {
        if (!newConfiguration.has(NEIGHBORS)) {
            throw new IllegalPropertyException(NEIGHBORS, "not defined");
        }
        /*if(!(newConfiguration.get(NEIGHBORS) instanceof JSONArray)) {
            throw new IllegalPropertyException(NEIGHBORS, "not an array");
        }*/

        JSONArray array = new JSONArray(newConfiguration.getString(NEIGHBORS));

        if (array.length() > 3) {
            throw new IllegalPropertyException(NEIGHBORS, "maximum 3 neighbors allowed");
        }

        for (int i = 0; i < array.length(); i++) {
            if (!(array.get(i) instanceof JSONObject)) {
                throw new IllegalPropertyException(NEIGHBORS, "array element at index " + i + " is not a JSONObject");
            }
        }
    }

    public String getIctVersion() {
        return ictVersion;
    }

    public void setIctVersion(String ictVersion) {
        this.ictVersion = ictVersion;
    }

    public int getIctRoundDuration() {
        return ictRoundDuration;
    }

    public void setIctRoundDuration(int ictRoundDuration) {
        this.ictRoundDuration = ictRoundDuration;
    }

    public int getIctRestPort() {
        return ictRestPort;
    }

    public void setIctRestPort(int ictRestPort) {
        this.ictRestPort = ictRestPort;
    }

    public String getIctRestPassword() {
        return ictRestPassword;
    }

    public void setIctRestPassword(String ictRestPassword) {
        this.ictRestPassword = ictRestPassword;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPublicAddress() {
        return publicAddress;
    }

    public void setPublicAddress(String publicAddress) {
        this.publicAddress = publicAddress;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    public List<Neighbor> getNeighbors() {
        synchronized (this.neighbors) {
            return new LinkedList<>(this.neighbors);
        }
    }

    private class IllegalPropertyException extends IllegalArgumentException {
        private IllegalPropertyException(String field, String cause) {
            super("Invalid property '" + field + "': " + cause + ".");
        }
    }

    public void syncIct() {
        syncIctConfig();
        syncIctInfo();
        syncIctNeighbors();
    }

    public void syncIctConfig() {
        final JSONObject response = IctRestCaller.getConfig(getIctRestPort(), getIctRestPassword());

        if (response != null) {
            setIctRoundDuration(response.getNumber("round_duration").intValue());
        }
    }

    public void syncIctInfo() {
        final JSONObject response = IctRestCaller.getInfo(getIctRestPort(), getIctRestPassword());

        if (response != null) {
            setIctVersion(response.getString("version"));
        }
    }

    public void syncIctNeighbors() {
        final JSONArray response = IctRestCaller.getNeighbors(getIctRestPort(), getIctRestPassword());

        final List<Neighbor> keepNeighbors = new LinkedList<>();

        for (int i = 0; response != null && i < response.length(); i++) {
            final JSONObject ictNeighbor = (JSONObject) response.get(i);
            final String ictNeighborAddress = ictNeighbor.getString("address");
            final JSONArray ictNeighborStatsArray = ictNeighbor.getJSONArray("stats");

            Neighbor neighbor = getNeighborByStaticAddress(ictNeighborAddress);
            if (neighbor == null) {
                neighbor = new Neighbor(ictNeighborAddress);
            }

            final JSONObject ictNeighborStats = getIctNeighborStats(ictNeighborStatsArray);
            if (ictNeighborStats != null) {
                neighbor.setTimestamp(ictNeighborStats.getNumber("timestamp").longValue());
                neighbor.setAllTx(ictNeighborStats.getInt("all"));
                neighbor.setNewTx(ictNeighborStats.getInt("new"));
                neighbor.setIgnoredTx(ictNeighborStats.getInt("ignored"));
                neighbor.setInvalidTx(ictNeighborStats.getInt("invalid"));
                neighbor.setRequestedTx(ictNeighborStats.getInt("requested"));
            }

            if (!neighbor.getPublicAddress().isEmpty()) {
                // The user has intentionally specified a publicAddress for this neighbor
                neighbor.setUuid(UuidGenerator.generate(neighbor.getPublicAddress()));
                log.debug(String.format(
                        "Assigned uuid %s to neighbor %s based upon the publicAddress specified by the user",
                        neighbor.getUuid(),
                        neighbor)
                );
            } else {
                // No public address was specified for this neighbor, assume that address is the publicAddress
                neighbor.setUuid(UuidGenerator.generate(neighbor.getAddress()));
                log.debug(String.format(
                        "Assigned uuid %s to neighbor %s based upon the address received from Ict REST API",
                        neighbor.getUuid(),
                        neighbor)
                );
            }

            keepNeighbors.add(neighbor);
        }

        // Update the neighbor list. Neighbors that are not represented in Ict are discarded.
        synchronized (this.neighbors) {
            neighbors.clear();
            neighbors.addAll(keepNeighbors);
        }
    }

    private Neighbor getNeighborByStaticAddress(String staticAddress) {
        for (Neighbor neighbor : getNeighbors()) {
            if (neighbor.getAddress().equals(staticAddress)) {
                return neighbor;
            }
        }
        return null;
    }

    private JSONObject getIctNeighborStats(JSONArray ictNeighborStatsArray) {
        if (getIctVersion().equals("0.5")) {
            // If this version of Report.ixi is operated on Ict 0.5, it will always try to get
            // the last metrics/stats record from Ict api.
            if (ictNeighborStatsArray.length() > 0) {
                return ictNeighborStatsArray.getJSONObject(ictNeighborStatsArray.length() - 1);
            }
        } else {
            // If this version of Report.ixi is on any newer version than Ict 0.5, it will always
            // try to get the second to last metrics/stats record from Ict api.
            if (ictNeighborStatsArray.length() > 1) {
                return ictNeighborStatsArray.getJSONObject(ictNeighborStatsArray.length() - 2);
            }
        }
        return null;
    }
}
