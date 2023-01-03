package data.scripts.world.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import data.scripts.AdversaryUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class AdversaryOptimal {
    public void generate(AdversaryUtil util, SectorAPI sector, JSONObject systemSettings) throws JSONException {
        // Create the system and set its location
        float fringeRadius = systemSettings.getInt("fringeJumpPointOrbitRadius");
        StarSystemAPI system = sector.createStarSystem("Optimal");
        util.setLocation(system, (fringeRadius / 10) + 100f, systemSettings.getBoolean("enableRandomLocation"));

        // Generate the center stars
        util.addStarsInCenter(system, systemSettings);
        List<PlanetAPI> systemPlanetList = system.getPlanets();
        int numOfCenterStars = systemPlanetList.size();

        // Create Fringe Jump-point
        util.addJumpPoint(system, system.getCenter(), "Fringe Jump-point", fringeRadius);

        // Create planets from JSON list
        JSONArray planetList = systemSettings.getJSONArray("planetList");
        boolean hasFactionPresence = false;
        boolean hasLagrangeEntities = systemSettings.getBoolean("enableDefaultStableEntities");
        for (int i = 0, numOfPlanetsOrbitingCenter = 0; i < planetList.length(); i++) {
            // Creates planet with appropriate characteristics
            JSONObject planetOptions = planetList.getJSONObject(i);
            int planetIndex = planetOptions.getInt("focus");
            SectorEntityToken focus = planetIndex <= 0 ? system.getCenter() : systemPlanetList.get(numOfCenterStars + planetIndex - 1);
            PlanetAPI newPlanet = util.addPlanet(system, focus, i, planetOptions);

            if (!newPlanet.isStar()) {
                newPlanet.setId("system_Optimal:planet_" + i); // Override the default ids
                String planetFaction = newPlanet.getFaction().getId();

                // Check if the system is occupied by a faction
                if (planetFaction != null && !planetFaction.equals("neutral")) hasFactionPresence = true;

                // Adds solar mirrors and shades if applicable
                if (newPlanet.hasCondition("solar_array")) util.addSolarArrayToPlanet(newPlanet, planetFaction);

                // Adds campaign entities at lagrange points of the first two planets orbiting the center
                if (hasLagrangeEntities && newPlanet.getOrbitFocus().isSystemCenter()) {
                    switch (numOfPlanetsOrbitingCenter++) {
                        case 1: // 2nd planet to orbit star
                            // Create nav buoy and sensor array on second planet's L4 and L5 points
                            util.addToLagrangePoints(newPlanet, null, util.addObjective(system, "nav_buoy", planetFaction), util.addObjective(system, "sensor_array", planetFaction));
                            break;
                        case 0: // 1st planet to orbit star
                            // Create comm relay, inactive gate, and inner system jump-point on first planet's Lagrange points
                            JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint(null, "Inner System Jump-point");
                            jumpPoint.setStandardWormholeToHyperspaceVisual();
                            system.addEntity(jumpPoint);
                            util.addToLagrangePoints(newPlanet, util.addObjective(system, "comm_relay", planetFaction), system.addCustomEntity(null, null, "inactive_gate", null), jumpPoint);
                            break;
                    }
                }
            } else {
                newPlanet.setId("system_Optimal:star_" + i);
            }
        }

        // Add the system features
        JSONArray systemFeatures = systemSettings.getJSONArray("systemFeatures");
        for (int i = 0; i < systemFeatures.length(); i++) {
            JSONObject feature = systemFeatures.getJSONObject(i);
            util.addSystemFeature(system, numOfCenterStars, feature);
        }

        // Adds a coronal hypershunt if enabled
        if (systemSettings.getBoolean("addCoronalHypershunt")) util.addHypershunt(system, !hasFactionPresence, true);

        // Adds a Domain-era cryosleeper if enabled
        if (systemSettings.getBoolean("addDomainCryosleeper"))
            util.addCryosleeper(system, "Domain-era Cryosleeper \"Sisyphus\"", fringeRadius + 4000f, !hasFactionPresence);

        // Add relevant system tags
        system.removeTag(Tags.THEME_CORE); // Technically not part of the Core Worlds
        system.addTag(Tags.THEME_INTERESTING);

        // Adds a hidden supply cache containing either the Hypershunt Tap or the Dealmaker Holosuite
        if (hasFactionPresence) {
            SalvageEntityGenDataSpec.DropData drop = new SalvageEntityGenDataSpec.DropData();
            drop.chances = 1;
            drop.addCustom("item_coronal_portal", 1f);
            drop.addCustom("item_dealmaker_holosuite", 1f);

            util.addSalvageEntity(system, "supply_cache_small", system.getCenter(), fringeRadius + 7777f).addDropRandom(drop);
        }

        util.setDefaultLightColorBasedOnStars(system, numOfCenterStars);
        util.generateHyperspace(system);
    }
}