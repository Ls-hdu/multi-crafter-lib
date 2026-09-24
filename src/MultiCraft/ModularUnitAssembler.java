package MultiCraft;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Scaling;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.ai.UnitCommand;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.type.PayloadStack;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.world.blocks.ItemSelection;
import mindustry.world.blocks.units.UnitAssembler;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatValues;


public class ModularUnitAssembler extends UnitAssembler {


    public @Nullable UnitPlanJson[] unitPlans;

    public ModularUnitAssembler(String name) {
        super(name);
        configurable = true;
        saveConfig = true;
        config(Integer.class, (ModularUnitAssemblerBuild build, Integer value) -> {
            if (value >= 0 && value < plans.size) {
                if (build.selectedPlan != value) {
                    build.selectedPlan = value;
                    build.progress = 0f;
                }
            }
        });

        config(UnitCommand.class, (ModularUnitAssemblerBuild build, UnitCommand cmd) -> {
            build.command = cmd;
        });
        configClear((ModularUnitAssemblerBuild build) -> build.command = null);
    }

    @Override
    public void init() {
        parseJsonPlans();
        super.init();
    }

    private void parseJsonPlans() {
        if (unitPlans == null || unitPlans.length == 0) return;

        for (UnitPlanJson j : unitPlans) {
            if (j == null || j.unit == null) continue;

            Seq<PayloadStack> reqs = new Seq<>();
            if (j.requirements != null) {
                reqs.addAll(j.requirements);
            }

            TieredPlan plan = new TieredPlan(j.unit, j.time, reqs, j.tier);
            plan.itemReq = j.itemReq;
            plan.liquidReq = j.liquidReq;

            plans.add(plan);
        }

        unitPlans = null;
    }





    public static class UnitPlanJson {
        public UnitType unit;
        public float time = 60f * 60f;
        public int tier = 0;
        public @Nullable PayloadStack[] requirements;
        public @Nullable ItemStack[] itemReq;
        public @Nullable LiquidStack[] liquidReq;

        public UnitPlanJson() {}
    }


    public static class TieredPlan extends AssemblerUnitPlan {
        public int tier;

        public TieredPlan(UnitType unit, float time, Seq<PayloadStack> requirements, int tier) {
            super(unit, time, requirements);
            this.tier = tier;
        }

        public TieredPlan() {
            super(null, 0f, new Seq<>());
        }
    }


    @Override
    public void setStats() {
        super.setStats();
        stats.remove(Stat.output);

        stats.add(Stat.output, table -> {
            table.row();


            for (AssemblerUnitPlan plan : plans) {
                int tier = plan instanceof TieredPlan tp ? tp.tier : 0;
                table.table(Styles.grayPanel, t -> {

                    if(plan.unit.isBanned()){
                        t.image(Icon.cancel).color(Pal.remove).size(40).pad(10);
                        return;
                    }

                    if(plan.unit.unlockedNow()){
                        t.image(plan.unit.uiIcon).scaling(Scaling.fit).size(40).pad(10f).left().with(i -> StatValues.withTooltip(i, plan.unit));
                        t.table(info -> {
                            info.defaults().left();
                            info.add(plan.unit.localizedName);
                            info.row();
                            info.add(Strings.autoFixed(plan.time / 60f, 1) + " " + Core.bundle.get("unit.seconds")).color(Color.lightGray);
                            if(tier > 0){
                                info.row();
                                info.add(Stat.moduleTier.localized() + ": " + tier).color(Color.lightGray);
                            }
                        }).left();

                        t.table(req -> {
                            req.add().grow();

                            req.table(solid -> {
                                int length = 0;
                                if(plan.itemReq != null){
                                    for(int i = 0; i < plan.itemReq.length; i++){
                                        if(length % 6 == 0){
                                            solid.row();
                                        }
                                        solid.add(StatValues.stack(plan.itemReq[i])).pad(5);
                                        length++;
                                    }
                                }

                                for(int i = 0; i < plan.requirements.size; i++){
                                    if(length % 6 == 0){
                                        solid.row();
                                    }
                                    solid.add(StatValues.stack(plan.requirements.get(i))).pad(5);
                                    length++;
                                }
                            }).right();

                            LiquidStack[] stacks = plan.liquidReq;
                            if(stacks != null){
                                for(int i = 0; i < plan.liquidReq.length; i++){
                                    req.row();

                                    req.add().grow();

                                    req.add(StatValues.displayLiquid(stacks[i].liquid, stacks[i].amount * 60f, true)).right();
                                }
                            }
                        }).grow().pad(10f);
                    }else{
                        t.image(Icon.lock).color(Pal.darkerGray).size(40).pad(10);
                    }
                }).growX().pad(5);
                table.row();
            }
        });
    }

    public class ModularUnitAssemblerBuild extends UnitAssemblerBuild {
        public @Nullable UnitCommand command;
        public int selectedPlan = 0;

        @Override
        public void configured(Unit builder, Object value) {
            super.configured(builder, value);
            if (value instanceof Integer i && i >= 0 && i < plans.size) {
                selectedPlan = i;
            }
        }

        @Override
        public AssemblerUnitPlan plan() {
            if (plans.isEmpty()) return null;
            if (selectedPlan < 0 || selectedPlan >= plans.size) selectedPlan = 0;

            AssemblerUnitPlan p = plans.get(selectedPlan);

            if (p instanceof TieredPlan tp && tp.tier > currentTier) {
                int fallback = findFirstValidPlan();
                if (fallback >= 0 && fallback != selectedPlan) {
                    selectedPlan = fallback;
                    progress = 0f;
                    p = plans.get(selectedPlan);
                }
            }

            return p;
        }

        private int findFirstValidPlan() {
            for (int i = 0; i < plans.size; i++) {
                AssemblerUnitPlan p = plans.get(i);
                if (!(p instanceof TieredPlan tp)) continue;
                if (tp.tier > currentTier) continue;
                if (!p.unit.unlockedNow() || p.unit.isBanned()) continue;
                return i;
            }
            return -1;
        }


        @Override
        public void spawned() {
            AssemblerUnitPlan plan = plan();
            if (plan == null) return;

            Vec2 spawn = getUnitSpawn();
            consume();
            Unit unit = plan.unit.create(team);

            if (unit.isCommandable()) {
                if (commandPos != null) {
                    unit.command().commandPosition(commandPos);
                }
                unit.command().command(command == null && unit.type.defaultCommand != null
                        ? unit.type.defaultCommand : command);
            }

            unit.set(spawn.x + Mathf.range(0.001f), spawn.y + Mathf.range(0.001f));
            unit.rotation = rotdeg();

            mindustry.gen.Building targetBuild = unit.buildOn();
            mindustry.world.blocks.payloads.UnitPayload payload =
                    new mindustry.world.blocks.payloads.UnitPayload(unit);

            if (targetBuild != null && targetBuild.team == team && targetBuild.acceptPayload(targetBuild, payload)) {
                targetBuild.handlePayload(targetBuild, payload);
            } else if (!Vars.net.client()) {
                unit.add();
                mindustry.entities.Units.notifyUnitSpawn(unit);
            }

            createSound.at(spawn.x, spawn.y, 1f + Mathf.range(0.06f), createSoundVolume);
            progress = 0f;
            mindustry.content.Fx.unitAssemble.at(spawn.x, spawn.y, rotdeg() - 90f, plan.unit);
            blocks.clear();
            arc.Events.fire(new mindustry.game.EventType.UnitCreateEvent(unit, this));
        }



        @Override
        public boolean shouldConsume() {
            if (plans.isEmpty()) return false;
            return super.shouldConsume();
        }

        public boolean canSetCommand(){
            var output = unit();
            return output != null && output.commands.size > 1 && output.allowChangeCommands;
        }

        @Override
        public void buildConfiguration(Table table) {
            Seq<UnitType> units = new Seq<>();
            Seq<Integer> indices = new Seq<>();

            for (int i = 0; i < plans.size; i++) {
                AssemblerUnitPlan p = plans.get(i);
                if (!(p instanceof TieredPlan tp)) continue;
                if (tp.tier > currentTier) continue;
                if (!p.unit.unlockedNow() || p.unit.isBanned()) continue;

                units.add(p.unit);
                indices.add(i);
            }

            if (units.any()) {
                ItemSelection.buildTable(
                        ModularUnitAssembler.this, table, units,
                        () -> {
                            if (selectedPlan < 0 || selectedPlan >= plans.size) return null;
                            return plans.get(selectedPlan).unit;
                        },
                        unit -> {
                            int idx = units.indexOf(unit);
                            if (idx != -1) configure(indices.get(idx));
                        },
                        selectionRows, selectionColumns
                );

                table.row();

                Table commands = new Table();
                commands.top().left();

                Runnable rebuildCommands = () -> {
                    commands.clear();
                    commands.background(null);

                    UnitType unit = unit();
                    if (unit != null && canSetCommand()) {
                        commands.background(Styles.black6);
                        ButtonGroup<ImageButton> group = new ButtonGroup<>();
                        group.setMinCheckCount(0);
                        int i = 0;
                        int columns = Mathf.clamp(units.size, 2, selectionColumns);
                        Seq<UnitCommand> list = unit.commands;

                        commands.image(Tex.whiteui, Pal.gray).height(4f).growX().colspan(columns).row();

                        for (UnitCommand item : list) {
                            ImageButton button = commands.button(item.getIcon(), Styles.clearNoneTogglei, 40f, () -> {
                                configure(item);
                            }).tooltip(item.localized()).group(group).get();

                            button.update(() -> button.setChecked(
                                    command == item || (command == null && unit.defaultCommand == item)));

                            if (++i % columns == 0) commands.row();
                        }

                        if (list.size < columns) {
                            for (int j = 0; j < columns - list.size; j++) {
                                commands.add().size(40f);
                            }
                        }
                    }
                };

                rebuildCommands.run();

                table.row();
                table.add(commands).fillX().left();

            } else {
                table.table(Styles.black3, t -> t.add("@none").color(Color.lightGray));
            }
        }

        @Override
        public Object config() {
            return selectedPlan;
        }

        @Override
        public byte version() {
            return 2;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.s(selectedPlan);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            if (revision >= 2) {
                selectedPlan = read.s();
            }
        }
    }
}