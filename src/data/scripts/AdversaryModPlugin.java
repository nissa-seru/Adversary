package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.AICoreAdminPluginImpl;
import data.scripts.world.systems.AdversaryCustomStarSystem;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

@SuppressWarnings("unused")
public class AdversaryModPlugin extends BaseModPlugin {
    private transient HashMap<MarketAPI, String> marketsToOverrideAdmin;

    // Reload doctrine changer since the Adversary's current doctrine get reset upon loading a new Starsector application.
    @Override
    public void onGameLoad(boolean newGame) {
        if (newGame) return;

        // TODO: remove everything related to the AdversaryFactionDoctrineChanger class if doing a major update
        Global.getSector().removeScriptsOfClass(AdversaryFactionDoctrineChanger.class); // For save-compatibility with v2.2.0 or earlier

        ListenerManagerAPI listMan = Global.getSector().getListenerManager();
        // Remove doctrine changer if disabled
        if (!Global.getSettings().getBoolean("enableAdversaryDoctrineChange"))
            listMan.removeListenerOfClass(AdversaryDoctrineChanger.class);
        else { // Doctrine changer is enabled
            List<AdversaryDoctrineChanger> doctrineListeners = listMan.getListeners(AdversaryDoctrineChanger.class);
            if (!doctrineListeners.isEmpty()) // Doctrine changer already in-game, so refresh the doctrine
                doctrineListeners.get(0).refresh();
            else try { // Doctrine changer not active, so add it to the current game
                listMan.addListener(new AdversaryDoctrineChanger("adversary", (short) 0, Global.getSettings().getJSONObject("adversaryDoctrineChangeSettings")));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Generates mod systems after proc-gen so that planet markets can properly generate
    @Override
    public void onNewGameAfterProcGen() {
        AdversaryUtil util = new AdversaryUtil();
        if (Global.getSettings().getBoolean("enableCustomStarSystems")) try {
            JSONArray systemList = Global.getSettings().getJSONArray("customStarSystems");
            for (int i = 0; i < systemList.length(); i++) {
                JSONObject systemOptions = systemList.getJSONObject(i);
                if (systemOptions.isNull("isEnabled") || systemOptions.getBoolean("isEnabled"))
                    for (int numOfSystems = systemOptions.isNull("numberOfSystems") ? 1 : systemOptions.getInt("numberOfSystems"); numOfSystems > 0; numOfSystems--)
                        new AdversaryCustomStarSystem().generate(util, systemOptions);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        marketsToOverrideAdmin = util.marketsToOverrideAdmin;
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        // Gives selected markets the admins they're supposed to have (can't do it before economy load)
        AICoreAdminPluginImpl aiPlugin = new AICoreAdminPluginImpl();
        for (MarketAPI market : marketsToOverrideAdmin.keySet()) {
            switch (marketsToOverrideAdmin.get(market)) {
                case "player":
                    market.setAdmin(null);
                    break;
                case "alpha_core":
                    market.setAdmin(aiPlugin.createPerson("alpha_core", market.getFaction().getId(), 0));
            }
        }
        // No need for the HashMap afterwards, so clear it and set it to null to minimize memory use, just in case
        marketsToOverrideAdmin.clear();
        marketsToOverrideAdmin = null;

        SectorAPI sector = Global.getSector();
        FactionAPI adversary = sector.getFaction("adversary");
        if (adversary != null) { // Null check so determined people can properly remove the faction from the mod without errors
            // Recent history has made them cold and hateful against almost everyone
            for (FactionAPI faction : sector.getAllFactions()) adversary.setRelationship(faction.getId(), -100f);
            adversary.setRelationship("adversary", 100f);
            adversary.setRelationship("neutral", 0f);

            // Allows the Adversary to change fleet doctrine in-game if enabled
            // Doing this here, so it can work during the initial 2-month time pass
            if (Global.getSettings().getBoolean("enableAdversaryDoctrineChange")) try {
                // reportEconomyMonthEnd() procs immediately when starting time pass, hence the -1 to account for that
                sector.getListenerManager().addListener(new AdversaryDoctrineChanger("adversary", (short) -1, Global.getSettings().getJSONObject("adversaryDoctrineChangeSettings")));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }
}