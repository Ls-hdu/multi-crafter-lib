package MultiCraft;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;

import arc.math.geom.Geometry;
import arc.util.Tmp;

import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.Duct;

import static mindustry.Vars.*;


public class HybridDuct extends Duct {

    public final int timerFlow = timers++;
    public boolean leaks = true;

    public HybridDuct(String name) {
        super(name);
        hasLiquids = true;
        liquidCapacity = 10f;

        buildType = HybridDuctBuild::new;

    }
    @Override
    public boolean blends(Tile tile, int rotation, int otherx, int othery, int otherrot, Block otherblock) {

        boolean itemBlend = (otherblock.outputsItems() ||
                (lookingAt(tile, rotation, otherx, othery, otherblock) && otherblock.hasItems))
                && lookingAtEither(tile, rotation, otherx, othery, otherrot, otherblock);

        boolean liquidBlend = otherblock.hasLiquids
                && (otherblock.outputsLiquid || lookingAt(tile, rotation, otherx, othery, otherblock))
                && lookingAtEither(tile, rotation, otherx, othery, otherrot, otherblock);


        return itemBlend || liquidBlend;
    }


    public class HybridDuctBuild extends DuctBuild {
        public float smoothLiquid;




        @Override
        public void updateTile() {






            super.updateTile();
            smoothLiquid = Mathf.lerpDelta(smoothLiquid, liquids.currentAmount() / liquidCapacity, 0.05f);
            if (liquids.currentAmount() > 0.0001f && timer(timerFlow, 1)) {
                moveLiquidForward(leaks, liquids.current());
            }


            if (current == null && items.total() == 0 && liquids.currentAmount() <= 0.0001f) {
                sleep();
            } else {
                noSleep();
            }
        }
        @Override
        public void draw(boolean under) {
            if (!under && smoothLiquid > 0.001f && liquids.currentAmount() > 0.001f) {
                Liquid liq = liquids.current();
                TextureRegion liquidr = renderer.fluidFrames[liq.gas ? 1 : 0][liq.getAnimationFrame()];
                Draw.z(Layer.blockUnder + 0.15f);
                Drawf.liquid(liquidr, x, y, smoothLiquid, liq.color);
            }

            super.draw(under);
        }


        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            noSleep();
            return (liquids.current() == liquid || liquids.currentAmount() < 0.2f)
                    && (tile == null || source == this || (source.relativeTo(tile.x, tile.y) + 2) % 4 != rotation);
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount) {
            liquids.add(liquid, amount);
            noSleep();
        }


    }
}