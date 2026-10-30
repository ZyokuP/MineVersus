package com.example.versus;

import com.example.versus.network.ModNetwork;
import net.minecraftforge.fml.common.Mod;

@Mod(VersusMod.MODID)
public class VersusMod {
    public static final String MODID = "versus";

    public VersusMod() {
        ModNetwork.registrar();
    }
}
