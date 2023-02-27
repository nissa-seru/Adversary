package data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionDoctrineAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.Set;

public class AdversaryDoctrineChanger implements EconomyTickListener {
    protected final static Logger log = Global.getLogger(AdversaryDoctrineChanger.class); // For debugging

    protected String factionId;
    protected short elapsedMonths;
    protected short delayInMonths; // How often this faction switches doctrines

    protected final byte[] doctrineList = {3, 2, 1, 0}; // Each index represents a doctrine number
    protected Random factionDoctrineSeed = new Random();
    protected WeightedRandomPicker<PriorityDoctrine> warshipGroups = new WeightedRandomPicker<>();
    protected WeightedRandomPicker<PriorityDoctrine> carrierGroups = new WeightedRandomPicker<>();
    protected WeightedRandomPicker<PriorityDoctrine> phaseShipGroups = new WeightedRandomPicker<>();
    protected WeightedRandomPicker<PriorityDoctrine> balancedGroups = new WeightedRandomPicker<>();
    protected PriorityDoctrine selectedPriority;

    public AdversaryDoctrineChanger(String faction, short elapsed, JSONObject doctrineSettings) throws JSONException {
        factionId = faction;
        elapsedMonths = elapsed;
        delayInMonths = (short) Math.max(doctrineSettings.getInt("doctrineChangeDelay"), 1); // Minimum of 1 month delay
        getDoctrineGroups(warshipGroups, doctrineSettings.getJSONArray("warships"));
        getDoctrineGroups(carrierGroups, doctrineSettings.getJSONArray("carriers"));
        getDoctrineGroups(phaseShipGroups, doctrineSettings.getJSONArray("phaseShips"));
        getDoctrineGroups(balancedGroups, doctrineSettings.getJSONArray("balanced"));
        log.info("Faction doctrine changer active for: " + factionId);
    }

    // Unused
    @Override
    public void reportEconomyTick(int iterIndex) {
    }

    // Change fleet doctrine if enough months have passed; cannot choose the same doctrine twice in a row
    @Override
    public void reportEconomyMonthEnd() {
        elapsedMonths++;
        if (elapsedMonths >= delayInMonths) {
            elapsedMonths = 0;
            int randomNum = factionDoctrineSeed.nextInt(3); // doctrineList.length - 1, which excludes the last index

            byte selectedDoctrine = doctrineList[randomNum];
            switch (selectedDoctrine) {
                case 3:  // Warship-focused
                    setFleetDoctrine(5, 1, 1);
                    setPriorityDoctrine(warshipGroups.pick(factionDoctrineSeed));
                    break;
                case 2:  // Carrier-focused
                    setFleetDoctrine(1, 5, 1);
                    setPriorityDoctrine(carrierGroups.pick(factionDoctrineSeed));
                    break;
                case 1:  // Phase-focused
                    setFleetDoctrine(1, 1, 5);
                    setPriorityDoctrine(phaseShipGroups.pick(factionDoctrineSeed));
                    break;
                default: // Balanced
                    setFleetDoctrine(3, 2, 2);
                    setPriorityDoctrine(balancedGroups.pick(factionDoctrineSeed));
            }

            // Prevent selected doctrine from being picked again next cycle
            doctrineList[randomNum] = doctrineList[3];
            doctrineList[3] = selectedDoctrine;
        }
    }

    // Refreshes the currently-set doctrine
    public void refresh() {
        switch (doctrineList[3]) {
            case 3:  // Warship-focused
                setFleetDoctrine(5, 1, 1);
                break;
            case 2:  // Carrier-focused
                setFleetDoctrine(1, 5, 1);
                break;
            case 1:  // Phase-focused
                setFleetDoctrine(1, 1, 5);
                break;
            default: // Balanced
                setFleetDoctrine(3, 2, 2);
        }
        if (selectedPriority != null) setPriorityDoctrine(selectedPriority);
    }

    // Initializes a weighted list for a specific doctrine
    protected void getDoctrineGroups(WeightedRandomPicker<PriorityDoctrine> picker, JSONArray doctrineGroups) throws JSONException {
        int numOfGroups = doctrineGroups.length();
        if (numOfGroups == 0) { // No doctrine groups, so default to always no priority ships, weapons, or fighters
            picker.init(1);
            picker.add(0, new PriorityDoctrine(), 1);
            return;
        }

        // Add priority groups to the doctrine's group picker
        picker.init(numOfGroups);
        for (int i = 0; i < numOfGroups; i++) {
            JSONObject thisPriority = doctrineGroups.getJSONObject(i);
            if (thisPriority.isNull("weight")) picker.add(i, new PriorityDoctrine(thisPriority), 1);
            else picker.add(i, new PriorityDoctrine(thisPriority), thisPriority.getInt("weight"));
        }
    }

    // Set this faction's fleets to a specified composition
    protected void setFleetDoctrine(int warships, int carriers, int phaseShips) {
        FactionDoctrineAPI factionDoctrine = Global.getSector().getFaction(factionId).getDoctrine();
        factionDoctrine.setWarships(warships);
        factionDoctrine.setCarriers(carriers);
        factionDoctrine.setPhaseShips(phaseShips);
        log.info(factionId + " fleet composition set to " + factionDoctrine.getWarships() + "-" + factionDoctrine.getCarriers() + "-" + factionDoctrine.getPhaseShips());
    }

    // Sets this faction's priority lists to a specific priority doctrine
    protected void setPriorityDoctrine(PriorityDoctrine thisPriority) {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        Set<String> factionShips = faction.getPriorityShips();
        factionShips.clear();
        Collections.addAll(factionShips, thisPriority.priorityShips);
        logPrioritySet(factionShips, "ships");

        Set<String> factionWeapons = faction.getPriorityWeapons();
        factionWeapons.clear();
        Collections.addAll(factionWeapons, thisPriority.priorityWeapons);
        logPrioritySet(factionWeapons, "weapons");

        Set<String> factionFighters = faction.getPriorityFighters();
        factionFighters.clear();
        Collections.addAll(factionFighters, thisPriority.priorityFighters);
        logPrioritySet(factionFighters, "fighters");

        faction.clearShipRoleCache(); // Required after any direct manipulation of faction ship lists
        selectedPriority = thisPriority;
    }

    protected void logPrioritySet(Set<String> set, String text) {
        if (set.isEmpty()) log.info(factionId + " has no priority " + text);
        else {
            StringBuilder contents = new StringBuilder();
            for (String s : set) contents.append(s).append(',');
            log.info(factionId + " priority " + text + ": [" + contents.deleteCharAt(contents.length() - 1) + "]");
        }
    }

    // A lighter, nested WeightedRandomPicker<T> designed specifically for this class
    protected static class WeightedRandomPicker<T> {
        private ArrayList<T> items;
        private float[] weights;
        private float total;

        // Initializes this Picker's class members using a specific length
        public void init(int length) {
            items = new ArrayList<>(length);
            weights = new float[length];
            total = 0f;
        }

        public void add(int i, T item, float weight) {
            if (weight <= 0) weight = 1; // Weight cannot be 0, so reset it to 1
            items.add(i, item);
            weights[i] = weight;
            total += weight;
        }

        // Picks a random item using a specific Random instance
        public T pick(Random randomSeed) {
            float random = randomSeed.nextFloat() * total;
            if (random > total) random = total;

            float weightSoFar = 0f;
            int index = 0;
            for (float weight : weights) {
                weightSoFar += weight;
                if (random <= weightSoFar) break;
                index++;
            }
            return items.get(Math.min(index, items.size() - 1));
        }
    }

    // A static nested class to make managing a faction's priority lists simpler
    protected static class PriorityDoctrine {
        public String[] priorityShips;
        public String[] priorityWeapons;
        public String[] priorityFighters;

        // Default priority doctrine, with no priority at all
        public PriorityDoctrine() {
            priorityShips = new String[0];
            priorityWeapons = new String[0];
            priorityFighters = new String[0];
        }

        // Creates a priority doctrine, storing lists of priority ships, weapons, and fighters
        public PriorityDoctrine(JSONObject priorityLists) throws JSONException {
            // Fill priority ships
            if (priorityLists.isNull("priorityShips")) priorityShips = new String[0];
            else {
                JSONArray shipList = priorityLists.getJSONArray("priorityShips");
                priorityShips = new String[shipList.length()];
                for (int i = 0; i < shipList.length(); i++) priorityShips[i] = shipList.getString(i);
            }

            // Fill priority weapons
            if (priorityLists.isNull("priorityWeapons")) priorityWeapons = new String[0];
            else {
                JSONArray weaponList = priorityLists.getJSONArray("priorityWeapons");
                priorityWeapons = new String[weaponList.length()];
                for (int i = 0; i < weaponList.length(); i++) priorityWeapons[i] = weaponList.getString(i);
            }

            // Fill priority fighters
            if (priorityLists.isNull("priorityFighters")) priorityFighters = new String[0];
            else {
                JSONArray fighterList = priorityLists.getJSONArray("priorityFighters");
                priorityFighters = new String[fighterList.length()];
                for (int i = 0; i < fighterList.length(); i++) priorityFighters[i] = fighterList.getString(i);
            }
        }
    }
}
