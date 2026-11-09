package fr.renblood.medievalcoins.client.economy;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;

/** Logical layout remains unchanged; pointer coordinates follow the same transform. */
public abstract class AdaptiveEconomyScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private double economyScale=1;
    protected AdaptiveEconomyScreen(T menu,Inventory inventory,Component title){super(menu,inventory,title);}
    @Override protected void init(){
        int viewportWidth=minecraft.getWindow().getGuiScaledWidth(),viewportHeight=minecraft.getWindow().getGuiScaledHeight();
        economyScale=FitLayout.scale(viewportWidth,viewportHeight,imageWidth,imageHeight);
        width=(int)Math.ceil(viewportWidth/economyScale);height=(int)Math.ceil(viewportHeight/economyScale);
        super.init();
        EconomyUi.request(economyResource());
    }
    protected String economyResource(){return "transaction";}
    @Override public final void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        graphics.pose().pushPose();graphics.pose().scale((float)economyScale,(float)economyScale,1);
        try{renderLogical(graphics,(int)(mouseX/economyScale),(int)(mouseY/economyScale),partial);
            EconomyUi.draw(graphics,leftPos,topPos+imageHeight+7,imageWidth,economyResource());
        }finally{graphics.pose().popPose();}
    }
    protected void renderLogical(GuiGraphics graphics,int mouseX,int mouseY,float partial){super.render(graphics,mouseX,mouseY,partial);}
    @Override public final boolean mouseClicked(double x,double y,int button){return logicalClick(x/economyScale,y/economyScale,button);}
    protected boolean logicalClick(double x,double y,int button){return super.mouseClicked(x,y,button);}
    @Override public final boolean mouseReleased(double x,double y,int button){return logicalRelease(x/economyScale,y/economyScale,button);}
    protected boolean logicalRelease(double x,double y,int button){return super.mouseReleased(x,y,button);}
    @Override public final boolean mouseDragged(double x,double y,int button,double dx,double dy){return logicalDrag(x/economyScale,y/economyScale,button,dx/economyScale,dy/economyScale);}
    protected boolean logicalDrag(double x,double y,int button,double dx,double dy){return super.mouseDragged(x,y,button,dx,dy);}
    @Override public final boolean mouseScrolled(double x,double y,double delta){return logicalScroll(x/economyScale,y/economyScale,delta);}
    protected boolean logicalScroll(double x,double y,double delta){return super.mouseScrolled(x,y,delta);}
    @Override public void mouseMoved(double x,double y){super.mouseMoved(x/economyScale,y/economyScale);}
}
