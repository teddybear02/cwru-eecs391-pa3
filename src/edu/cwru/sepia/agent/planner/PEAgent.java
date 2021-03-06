package edu.cwru.sepia.agent.planner;

import edu.cwru.sepia.action.*;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.agent.planner.actions.*;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.ResourceType;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Template;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.util.Direction;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * This is an outline of the PEAgent. Implement the provided methods. You may add your own methods and members.
 */
public class PEAgent extends Agent {

    // The plan being executed
    private Stack<StripsAction> plan = null;
    private Stack<StripsAction> previousStripsActions = new Stack<>();

    // maps the real unit Ids to the plan's unit ids
    // when you're planning you won't know the true unit IDs that sepia assigns. So you'll use placeholders (1, 2, 3).
    // this maps those placeholders to the actual unit IDs.
    private Map<Integer, Integer> peasantIdMap;
    private int townhallId;
    private int peasantTemplateId;

    /**
     * Construct a PEAgent that adheres to a plan.
     * @param playernum The player number associated with this agent
     * @param plan The plan that will lead this agent to victory
     */
    public PEAgent(int playernum, Stack<StripsAction> plan) {
        super(playernum);
        peasantIdMap = new HashMap<Integer, Integer>();
        this.plan = plan;
    }

    @Override
    public Map<Integer, Action> initialStep(State.StateView stateView, History.HistoryView historyView) {
        // gets the townhall ID and the peasant ID
        for (int unitId : stateView.getUnitIds(playernum)) {
            Unit.UnitView unit = stateView.getUnit(unitId);
            String unitType = unit.getTemplateView().getName().toLowerCase();
            if (unitType.equals("townhall")) {
                townhallId = unitId;
            } else if (unitType.equals("peasant")) {
                peasantIdMap.put(unitId, unitId);
            }
        }

        // Gets the peasant template ID. This is used when building a new peasant with the townhall
        for (Template.TemplateView templateView : stateView.getTemplates(playernum)) {
            if (templateView.getName().toLowerCase().equals("peasant")) {
                peasantTemplateId = templateView.getID();
                break;
            }
        }

        return middleStep(stateView, historyView);
    }

    /**
     * This is where you will read the provided plan and execute it. If your plan is correct then when the plan is empty
     * the scenario should end with a victory. If the scenario keeps running after you run out of actions to execute
     * then either your plan is incorrect or your execution of the plan has a bug.
     *
     * You can create a SEPIA deposit action with the following method
     * Action.createPrimitiveDeposit(int peasantId, Direction townhallDirection)
     *
     * You can create a SEPIA harvest action with the following method
     * Action.createPrimitiveGather(int peasantId, Direction resourceDirection)
     *
     * You can create a SEPIA build action with the following method
     * Action.createPrimitiveProduction(int townhallId, int peasantTemplateId)
     *
     * You can create a SEPIA move action with the following method
     * Action.createCompoundMove(int peasantId, int x, int y)
     *
     * these actions are stored in a mapping between the peasant unit ID executing the action and the action you created.
     *
     * For the compound actions you will need to check their progress and wait until they are complete before issuing
     * another action for that unit. If you issue an action before the compound action is complete then the peasant
     * will stop what it was doing and begin executing the new action.
     *
     * To check an action's progress you can call getCurrentDurativeAction on each UnitView. If the Action is null nothing
     * is being executed. If the action is not null then you should also call getCurrentDurativeProgress. If the value is less than
     * 1 then the action is still in progress.
     *
     * Also remember to check your plan's preconditions before executing!
     */
    @Override
    public Map<Integer, Action> middleStep(State.StateView stateView, History.HistoryView historyView) {
        Map<Integer, Action> actionMap = new HashMap<>();
        populateUnitMap(stateView);
        if (stateView.getTurnNumber() != 0) {
            for (ActionResult result :
                    historyView.getCommandFeedback(playernum, stateView.getTurnNumber() - 1).values()) {
                System.out.println(result);
                if (result.getFeedback() == ActionFeedback.INCOMPLETE) {
                    actionMap.put(result.getAction().getUnitId(), result.getAction());
                }
            }
            if (actionMap.isEmpty() && !plan.isEmpty()){
                StripsAction nextAction = plan.pop();
                actionMap.putAll(createSepiaAction(nextAction));
            }
        } else {
            actionMap.putAll(createSepiaAction(plan.pop()));
        }
        return actionMap;
    }

    /**
     * Populate the map of unit IDs using the provided stateview object.
     * @param stateView The current stateview for populating the unit ID map
     */
    private void populateUnitMap(State.StateView stateView) {
        int i = 0;
        for (Unit.UnitView unitView : stateView.getAllUnits()) {
            peasantIdMap.putIfAbsent(i++, unitView.getID());
        }
    }

    /**
     * Returns a SEPIA version of the specified Strips Action.
     * @param action StripsAction
     * @return SEPIA representation of same action
     */
    private Map<Integer, Action> createSepiaAction(StripsAction action) {
        Map<Integer, Action> actionMap = new HashMap<>();
        for (int i = 0; i <  action.getK(); i++) {
            int id = peasantIdMap.get(action.getUnit(i).getID());
            switch (action.getType()) {
                case MOVE:

                    actionMap.put(id, Action.createCompoundMove(
                            id,
                            ((MoveK)action).targetPosition(i).x,
                            ((MoveK)action).targetPosition(i).y
                    ));
                    break;
                case HARVEST:
                        actionMap.put(id, Action.createPrimitiveGather(
                                id,
                                action.getUnit(i).getPosition().getDirection(
                                        ((HarvestK) action).targetPosition(i))
                        ));
                    break;
                case DEPOSIT:
                    actionMap.put(id, Action.createPrimitiveDeposit(
                            id,
                            action.getUnit(i).getPosition().getDirection(
                                    action.targetPosition())
                    ));
                    break;
                case BUILD:
                    actionMap.put(townhallId, Action.createPrimitiveBuild(townhallId, peasantTemplateId));
                    break;
                default:
            }
        }
        return actionMap;
    }

    @Override
    public void terminalStep(State.StateView stateView, History.HistoryView historyView) {

    }

    @Override
    public void savePlayerData(OutputStream outputStream) {

    }

    @Override
    public void loadPlayerData(InputStream inputStream) {

    }
}
