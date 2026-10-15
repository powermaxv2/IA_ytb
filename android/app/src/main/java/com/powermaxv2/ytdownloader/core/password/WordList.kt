package com.powermaxv2.ytdownloader.core.password

// Words of the "Facile à retenir" passphrases: 1212 short, common French words, without accents (they type the
// same on every keyboard) and without anything hurtful. Each word drawn adds log2(1212) ≈ 10.2 bits.

internal object WordList {
    val words: List<String> by lazy { RAW.split(' ').filter { it.isNotEmpty() } }

    private const val RAW =
        "abeille abri abricot absolu accord acier acrobate actif adagio adresse agile agneau aigle aiguille aile aimant " +
        "air album alerte algue allure alpage alto amande ambre ami amiral amour ampere ampoule ananas ancien ancre ange " +
        "angle anis anneau antenne aplomb appel aquarium arbre arc arche argent argile armure arome arrosoir artiste " +
        "asperge astre atelier atlas atome attente aube auberge audace aurore autobus automne autruche avenir averse " +
        "aveu avion avocat azur badge bagage bague baguette baie bain baleine balle ballon bambou banane banc bandeau " +
        "banque baobab barbe barque barrage bassin bateau baton baume bazar beau bec beige belette berceau berger besace " +
        "beton beurre biche bijou bille biscuit bison bistro bitume blague blanc blason bleu bloc blond bocage bocal " +
        "boeuf bois boite bol bonbon bonheur bonnet bonsai bord borne bosquet botte bouche bouee bougie boule bouleau " +
        "bouquet bourse boussole bouton boxe bracelet branche bras brave bravo brebis bretelle brin brindille brioche " +
        "brique brise brochet brosse brouette bruit brume brun buche buffet buisson bulle bureau cabane cabas cable " +
        "cacao cachet cactus cadeau cadre cage cahier caillou caisse cajou calcul calepin calme camion camp canal canard " +
        "canif canne canon canot canyon cape capitaine capuche carafe caramel carillon carnet carotte carpe carre carte " +
        "carton cascade casque castor cave cedre ceinture cerceau cerf cerise cerveau chaise chalet chameau champ chance " +
        "chanson chant chapeau charbon chariot chasse chat chateau chaton chaud chemin chene chenille cheval cheveu " +
        "chevre chien chiffre chignon chocolat chou chouette ciel cigale cigogne cime cinema cirque citron civet clair " +
        "clairon claque classe clavier clef climat cloche clou clown cobalt cobra coco cocon code coeur coffre coin col " +
        "colibri colline colombe comete compas comptoir concert condor confiture conte coq coquille corail corbeau corde " +
        "cordon corne corps corsaire costume cotillon coton cou coucou couleur coupe courage courbe couronne cours " +
        "course coussin couteau crabe craie crane crapaud cratere cravate crayon creme crepe creux cri criquet cristal " +
        "crochet crocus croix crumble cube cuillere cuir cuivre cumin cyclone cygne dahlia dalle dame danse dauphin " +
        "debut decor defi degel delice delta dent dentelle desert dessin destin detour devin diamant digue dinde dindon " +
        "dire disque divan docteur doigt dolmen domino donjon dos dossier douane doux dragee dragon drap drapeau droit " +
        "dune duo duvet eau ecaille echelle echo eclair ecole ecorce ecran ecrin ecureuil eden effort elan elephant elfe " +
        "email emeraude empire enclos encre energie enfant enigme envol epee epi epice epine eponge equipe erable escale " +
        "escalier escargot espace espoir esquimau essai est etang etincelle etoile etude eventail expert fable facteur " +
        "faim falaise fanfare fanion farfadet farine faucon faune fauteuil fenetre fennec fer ferme festin feu feuille " +
        "feutre fiacre ficelle figue fil filet fiole flamant flambeau flanelle fleur fleuve flocon flute foin foire fond " +
        "fontaine force foret forge fort fosse foudre fougere foulard four fourmi frais fraise framboise frein frene " +
        "frere fresque frimas frisson froid fromage front fruit fumee furet fusain fuseau fusee futur gabarit gadget " +
        "galaxie galet galop gamin gant garage gardon gare gateau gazelle gazon geant gecko gel genou geyser gilet " +
        "girafe girouette givre glace glaive gland globe gloire gnome goeland golf gomme gondole gorille goujon gourde " +
        "goutte grain graine grand grange grappe gras gravure grelot grenier griffe grillon griotte gris grotte groupe " +
        "grue guepe guide guimauve guitare habit hache halte hamac hameau hamster hangar haricot harpe hasard hausse " +
        "herbe hermine heron hibou hiver homard honneur horizon horloge hotel hotte houle houx hublot huile huitre " +
        "humour hutte hymne ibis icone idee igloo ile image impact index indigo insecte iris ivoire jade jaguar jambe " +
        "jardin jasmin jaune javelot jean jeton jeu jeudi joie jongleur jonquille joue jouet jour joyau juge jumeau " +
        "jungle jupe jus kayak kermesse kiosque kiwi koala lac lacet lagon lagune laine lait laitue lama lame lampe " +
        "lampion lance landau lande langue lanterne lapin larme laser laurier lavande lavoir lecture legende legume lent " +
        "lentille lettre levier lezard liane libellule libre licorne lien lierre lievre lilas lime limon lin linge lion " +
        "lis lit litre livre loge loi long loriot losange loto loup loupe loutre luciole lueur luge lumiere lundi lune " +
        "lustre lutin lynx lyre macaron magie maille main mais maison major malle mammouth manche mandarine manege " +
        "mangue manteau marbre marche mardi mare marelle marin marmotte marron mars mascotte masque mat matelas matelot " +
        "matin mauve melon menthe mer merci meringue merle mesange metal meteo metre meule miel miette mille mimosa mine " +
        "minute mirabelle miroir mistral mode moine moineau moins mois moka monde mont montagne montre morse mosaique " +
        "mot mouche mouette moufle moulin mousse mouton moyen muguet mur muret muscade muse musee musique myrtille " +
        "mystere nacre nage naif nain nappe narval navet navire nectar nef neige nenuphar neuf nez nid noble noce noeud " +
        "noir noisette noix nom nord noria note nougat nouveau nuage nuit numero oasis objet ocean ocre odeur oeil oeuf " +
        "ogre oie oignon oiseau olive ombre once onde ongle opale opera orage orange orchidee ordre oreille orge orgue " +
        "orque otarie ouest ouistiti ouragan ours outil oval ozone page paille pain paix palais palme palmier pampa " +
        "panda panier panneau papier papillon paquet paravent parc parfum pari parole passage pastel patate pate patin " +
        "paume pavot pays peche pecheur peigne peintre pelican pelle pelote pendule pepin pepite periple perle perroquet " +
        "persil petale petit pharaon phare photo piano pic pied pierre pigeon pile pilote pin pinceau pingouin pinson " +
        "pion pipe pirate pistache piste piton pivoine place plage plaine plan plancton planete plante plat plateau " +
        "plein pluie plume plumeau poche poele poeme poire pois poisson poivre poivron polaire pole pomme pompe pompon " +
        "pont port porte poste pot potage pouce poudre poulain poule poulpe poupee pourpre prairie praline primevere " +
        "prince prisme prix profond proue prune puce puits pull puma pupitre puzzle pyjama python quai quartz quatre " +
        "quetzal quiche quille quinoa racine radar radeau radio radis rafale raisin rame rampe rapide raquette rare " +
        "rasoir rat raton ravioli rayon recif record refuge regard reine relais renard renne repas requin reseau reve " +
        "revue riche ricochet ride rideau rire riviere riz robe robot roche rocher roi ronce rond rose roseau rosee " +
        "rossignol roue rouge rouleau route ruban rubis ruche rue ruisseau rumeur ruse sable sabot sabre sac safari " +
        "safran sage saison salade salon samedi sandale santon saphir sapin sarbacane sardane sardine satin sauce saule " +
        "saumon saut sauvage savane savon scene scie seau sel selle semaine sentier serpent serre seuil sieste sifflet " +
        "signal signe silence singe sirene sirop ski socle soie soif soir soleil solide sombre sommet sonnette sorbet " +
        "sorcier soucoupe souffle soupe source souris sous sphere spirale stade statue stylo sucre sud sultan super " +
        "sureau surf sylve table tableau tablier tache taille talon tamaris tambour tamis tango tante tanuki tapis tarte " +
        "tartine tasse tatou taupe taureau taxi temple temps tenor tente terre terrier tete tetras the theatre thon " +
        "tigre tilleul timbre tipi tiroir tissu titan toboggan toile toit tomate tonneau tonnerre topaze torche tornade " +
        "torrent tortue totem toucan tour tournesol tournoi tracteur train trait trapeze trefle tresor treve tribu " +
        "tricot trio trombone trompe tronc trophee trou troupeau truite tuba tuile tulipe tunnel turbo turquoise tutu " +
        "tuyau ulysse unique univers urne usine utile vache vague vaisseau valise vallee valse vanille vapeur vase vaste " +
        "veau velo velours vendredi vent ventre verger vermeil vernis verre vert veste vie vigne village ville vin " +
        "violette violon vipere virage vis visage vitesse vitre vivant voile voilier voisin voiture voix vol volcan " +
        "volute voyage vrai wagon wapiti yaourt yeti yoga yole zebre zenith zephyr zero zeste zigzag zinc zone zoo "
}
