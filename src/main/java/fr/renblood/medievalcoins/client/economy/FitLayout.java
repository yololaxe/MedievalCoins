package fr.renblood.medievalcoins.client.economy;
public final class FitLayout {
    private FitLayout(){}
    public static double scale(int viewportWidth,int viewportHeight,int width,int height){
        return Math.max(.1D,Math.min(1D,Math.min((viewportWidth-16D)/Math.max(1,width),(viewportHeight-16D)/Math.max(1,height+64))));
    }
    public static double logical(double coordinate,double scale){return coordinate/scale;}
}
