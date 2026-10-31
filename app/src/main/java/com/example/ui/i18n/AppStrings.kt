package com.example.ui.i18n

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.data.AppLanguage

/**
 * Conjunto completo de textos traduzidos da aplicação (en-US e pt-BR):
 * Abertura, Boas-Vindas & Idioma, Hub do Menu Principal, Prévia da Etapa 3,
 * Configurações, Viewport 3D, Guia de Notação, Modo Explorar, Índice do Curso,
 * Telas de Lição (Modo Aprender e Modo Praticar), Painel Pedagógico Expansível,
 * Modal Detalhado de Casos, Guia Visual dos "Faróis" da Etapa 6 e Mensagens de Feedback.
 *
 * Suporta atualização reativa imediata sem reiniciar o aplicativo.
 */
const val OFFICIAL_APP_NAME = "Cube Master 3D"

@Immutable
data class AppStrings(
    val language: AppLanguage,

    // Identidade e Cabeçalho (marca fixa em inglês em todos os idiomas)
    val appName: String = OFFICIAL_APP_NAME,
    val homeAppName: String = OFFICIAL_APP_NAME,
    val homeSubtitle: String,
    val interactiveCubeBadge: String,
    val restoreCubeButton: String,
    val settingsButton: String,

    // OpeningScreen
    val openingBadge: String,
    val openingSubtitle: String,
    val openingFeatureLearn: String,
    val openingFeatureExplore: String,
    val openingFeatureSolve: String,
    val openingContinueButton: String,
    val openingVersionLabel: String,

    // WelcomeLanguageScreen
    val welcomeBadge: String,
    val welcomeTitle: String,
    val welcomeSubtitle: String,
    val welcomeFeatureInteractive: String,
    val welcomeFeatureMethod: String,
    val welcomeSelectLanguageTitle: String,
    val welcomeSelectLanguageHint: String,
    val welcomeGetStartedButton: String,

    // HomeScreen - Card Destaque Etapa 3 (Resolver Meu Cubo)
    val solveMyCubeTitle: String,
    val solveMyCubeStage3Badge: String,
    val solveMyCubeDescription: String,
    val solveWithCameraLabel: String,
    val solveManuallyLabel: String,

    // HomeScreen - Progresso e Ações
    val studentProgressTitle: String,
    val stagesCompletedLabel: (Int) -> String,
    val continueStageFormat: (Int, String) -> String,
    val startFromStage1Label: String,
    val startLearningButton: String,
    val continueButton: String,
    val practiceButton: String,
    val exploreCubeButton: String,
    val notationGuideButton: String,

    // Modal da Etapa 3 (Resolver Meu Cubo)
    val solveModalBadge: String,
    val solveModalTitle: String,
    val solveModalDescription: String,
    val solveModalCameraTitle: String,
    val solveModalCameraBody: String,
    val solveModalManualTitle: String,
    val solveModalManualBody: String,
    val solveModalTryLearnNow: String,
    val solveModalCloseButton: String,

    // Modal de Configurações (Etapa 1)
    val settingsModalTitle: String,
    val settingsModalSubtitle: String,
    val settingsLanguageSectionTitle: String,
    val settingsThemeSectionTitle: String,
    val settingsThemeDarkLabel: String,
    val settingsThemeLightLabel: String,
    val settingsSpeedSectionTitle: String,
    val settingsHighlightPiecesLabel: String,
    val settingsHighlightPiecesSubtitle: String,
    val settingsReopenWelcomeButton: String,
    val settingsDoneButton: String,

    // Viewport 3D e Gestos (Cube3DView)
    val viewportTouchModeLayers: String,
    val viewportTouchModeCamera: String,
    val viewportCameraHint: String,
    val viewportRecenterCameraDescription: String,
    val cubeGestureHintFree: String,
    val cubeGestureHintCameraOnly: String,
    val cubeRecenterButton: String,
    val cubeRecenterContentDesc: String,
    val cubeDirectionClockwise: String,
    val cubeDirectionCounterClockwise: String,
    val cubeDirectionDouble180: String,
    val cubeViewportAccessibilityDesc: (Int) -> String,

    // Guia de Notação 3×3 (NotationGuideModal)
    val notationModalTitle: String,
    val notationModalSubtitle: String,
    val notationCloseContentDesc: String,
    val notationOrientationBanner: String,
    val notationRestoreButton: String,
    val notationRestoreContentDesc: String,
    val notationRepeatButton: String,
    val notationRepeatContentDesc: String,
    val notationHowToReadTitle: String,
    val notationHowToReadBody: String,
    val notationSubClockwise: String,
    val notationSubCounterClockwise: String,
    val notationSubDouble180: String,

    // Modo Explorar (ExploreScreen)
    val exploreTitle: String,
    val exploreDefaultSubtitle: String,
    val exploreBackContentDesc: String,
    val exploreNotationContentDesc: String,
    val exploreStatusSolvedFormat: (Int) -> String,
    val exploreStatusExploringFormat: (Int) -> String,
    val exploreLayersTitle: String,
    val exploreModifierNormal: String,
    val exploreModifierInverse: String,
    val exploreModifierDouble: String,
    val exploreScrambleButton: String,
    val exploreUndoButton: String,
    val exploreCameraButton: String,
    val exploreRestoreButton: String,
    val exploreFamilyShortLabel: (String) -> String,

    // Curso de 7 Etapas (CourseScreen)
    val courseTitle: String,
    val courseSubtitle: String,
    val courseBackContentDesc: String,
    val courseStudyFlowContentDesc: String,
    val courseNotationContentDesc: String,
    val courseProgressFormat: (Int) -> String,
    val courseStudyFlowTitle: String,
    val courseStudyFlowDismissDesc: String,
    val courseStudyFlowBody: String,
    val courseStepBadgeFormat: (Int, Int) -> String,
    val courseLearnButton: String,
    val coursePracticeButton: String,
    val courseRepeatStepDesc: String,
    val courseResetStageConfirmButton: String,

    // LessonScreen — Cabeçalho, Modos e Overlays do Viewport
    val lessonBackToCourseDesc: String,
    val lessonSpeedDesc: String,
    val lessonHighlightPiecesDesc: String,
    val lessonNotationGuideDesc: String,
    val lessonLShapeFormedBadge: String,
    val lessonDemoCompletedBanner: String,
    val lessonGoalAchievedBanner: String,

    // LessonScreen — Seletor de Casos e Barra de Orientação
    val lessonPrevCaseDesc: String,
    val lessonPrevCaseButton: String,
    val lessonCaseHeader: (Int, Int) -> String,
    val lessonNextCaseButton: String,
    val lessonNextCaseDesc: String,
    val lessonUnderstandCaseButton: String,
    val lessonCaseChip: (Int) -> String,
    val lessonCaseOrientationTip: String,
    val lessonDismissCaseTipDesc: String,

    // LessonScreen — Modo Aprender (Fases, Algoritmo e Controles de Reprodução)
    val lessonPlayPhase1Button: String,
    val lessonPlayPhase2Button: String,
    val lessonPlay2PhasesButton: String,
    val lessonPlayPhaseShortButton: (Int) -> String,
    val lessonPlayNPhasesButton: (Int) -> String,
    val lessonCurrentMoveStatus: (Int, Int, String, String) -> String,
    val lessonFirstMoveStatus: (String, String) -> String,
    val lessonAlgorithmDefaultTitle: String,
    val lessonFullAlgorithm2PhasesTitle: (Int) -> String,
    val lessonFullAlgorithm4PhasesTitle: (Int) -> String,
    val lessonAlgorithmTitle: (Int) -> String,
    val lessonCollapseAlgorithmButton: String,
    val lessonViewAllAlgorithmButton: String,
    val lessonPhaseTransitionLBadge: String,
    val lessonPhaseTransitionLeftPairBadge: String,
    val lessonPhaseTransitionBackPairBadge: String,
    val lessonPhaseTransition4PairsBadge: String,
    val lessonResetDemoDesc: String,
    val lessonPrevMoveDesc: String,
    val lessonPauseButton: String,
    val lessonPlayButton: String,
    val lessonNextMoveDesc: String,
    val lessonRepeatSequenceDesc: String,

    // LessonScreen — Modo Praticar
    val practiceFamilyTitle: (String) -> String,
    val practiceHideCompactButton: String,
    val practiceHideKeyboardButton: String,
    val practiceKeyboardCompactButton: String,
    val practiceFullKeyboardButton: String,
    val practiceToggleKeyboardDesc: String,
    val practiceUndoDesc: String,
    val practiceResetDesc: String,
    val practiceMoveNormalLabel: String,
    val practiceMoveInverseLabel: String,
    val practiceMoveDoubleLabel: String,

    // LessonScreen — Painel Pedagógico Expansível (3 Estados)
    val panelStepGoalPrefix: (String) -> String,
    val panelTipPrefix: (String) -> String,
    val panelStepHeader: (Int, String) -> String,
    val panelGoalPrefix: (String) -> String,
    val panelTeacherTipPrefix: (String) -> String,
    val panelNotationMoveExplanation: (String, String) -> String,

    // LessonScreen — Modal de Explicação Detalhada do Caso
    val caseModalStepHeader: (Int, String) -> String,
    val caseModalTitleHeader: (Int, Int, String) -> String,
    val caseModalCloseDesc: String,
    val caseModalWhatHappensTitle: String,
    val caseModalTeacherTipTitle: String,
    val caseModalFullAlgorithmTitle: (Int) -> String,
    val caseModalPhaseAlgorithmPrefix: (String) -> String,
    val caseModalMoveStepHeader: (Int, String, String) -> String,
    val caseModalStepGoalTitle: (Int) -> String,
    val caseModalOrientationTipTitle: String,
    val caseModalReturnToCubeButton: String,

    // LessonScreen — Guia Visual dos "Faróis" da Etapa 6
    val stage6HeadlightsOrangeLabel: String,
    val stage6HeadlightsGreenLabel: String,
    val stage6HeadlightsCase1FaceTitle: String,
    val stage6HeadlightsCase2FaceTitle: String,
    val stage6HeadlightsGuideTitle: String,
    val stage6HeadlightsTopYellowCaption: String,
    val stage6HeadlightsFirstCornerBadge: String,
    val stage6HeadlightsSecondCornerBadge: String,
    val stage6HeadlightsDiagramCaption: (String, String) -> String,
    val stage6HeadlightsSixRules: List<String>,
    val stage6HeadlightsExamplesTitle: String,
    val stage6HeadlightsExampleCase1: String,
    val stage6HeadlightsExampleCase2: String,

    // LessonScreen — Barra Inferior de Navegação entre Etapas
    val footerPrevStepDesc: String,
    val footerPrevStepButton: String,
    val footerCompleteStepDesc: String,
    val footerCompletedStepButton: String,
    val footerCompleteStepButton: String,
    val footerNextStepButton: String,
    val footerNextStepDesc: String,

    // CuboMestreViewModel — Mensagens de Feedback Dinâmicas
    val feedbackDemoCompleted: String,
    val feedbackMoveProgressFormat: (Int, Int, String, String) -> String,
    val feedbackStepRevertedFormat: (String) -> String,
    val feedbackStageCompleted: String,
    val feedbackMoveExecutedFormat: (String) -> String,
    val feedbackMoveUndone: String,
    val feedbackStageMarkedCompletedFormat: (Int) -> String,
    val feedbackExploreMoveFormat: (String, String) -> String,
    val feedbackExploreCubeSolved: String,
    val feedbackExploreUndoFormat: (String) -> String,
    val feedbackExploreScrambled: String,
    val feedbackExploreRestored: String
) {
    fun stagesCompletedFormat(completed: Int): String = stagesCompletedLabel(completed)
}

val PtBrStrings = AppStrings(
    language = AppLanguage.PT_BR,
    appName = OFFICIAL_APP_NAME,
    homeSubtitle = "Aprenda, explore e domine o cubo mágico 3×3 passo a passo.",
    interactiveCubeBadge = "Cubo 3×3 Interativo",
    restoreCubeButton = "Restaurar",
    settingsButton = "Ajustes",

    openingBadge = "MÉTODO DAS CAMADAS • 3D INTERATIVO",
    openingSubtitle = "Domine o cubo mágico 3×3 com visualização 3D interativa, algoritmos guiados e prática inteligente.",
    openingFeatureLearn = "7 Etapas Guiadas",
    openingFeatureExplore = "Simulador 360°",
    openingFeatureSolve = "26 Casos Reais",
    openingContinueButton = "Entrar na Experiência",
    openingVersionLabel = "Edição Educacional 3D • OpenGL ES 2.0",

    welcomeBadge = "BEM-VINDO • WELCOME",
    welcomeTitle = "Bem-vindo ao Cube Master 3D",
    welcomeSubtitle = "Selecione como deseja visualizar a interface do Cube Master 3D. Você poderá alterar o idioma a qualquer momento no menu principal.",
    welcomeFeatureInteractive = "Cubo 3D 360° em Tempo Real",
    welcomeFeatureMethod = "Método de Camadas Passo a Passo",
    welcomeSelectLanguageTitle = "Idioma do Aplicativo / App Language",
    welcomeSelectLanguageHint = "Sua escolha é salva automaticamente no dispositivo.",
    welcomeGetStartedButton = "Começar",

    solveMyCubeTitle = "Resolver meu cubo",
    solveMyCubeStage3Badge = "Etapa 3 • Em breve",
    solveMyCubeDescription = "Use o estado real do seu cubo físico para receber a solução guiada passo a passo.",
    solveWithCameraLabel = "Resolver com câmera",
    solveManuallyLabel = "Resolver manualmente",

    studentProgressTitle = "Progresso no Curso",
    stagesCompletedLabel = { count -> "$count de 7 etapas concluídas" },
    continueStageFormat = { num, title -> "Continuar: Etapa $num — $title" },
    startFromStage1Label = "Comece pela Etapa 1: Cruz Branca",
    startLearningButton = "Começar a aprender",
    continueButton = "Continuar Curso",
    practiceButton = "Praticar",
    exploreCubeButton = "Explorar Cubo",
    notationGuideButton = "Guia de Notação",

    solveModalBadge = "RECURSO DA ETAPA 3 DO PROJETO",
    solveModalTitle = "Resolver Meu Cubo Físico",
    solveModalDescription = "Na Etapa 3 do roteiro de evolução do Cube Master 3D, você poderá inserir as cores do seu cubo físico para gerar uma solução personalizada passo a passo:",
    solveModalCameraTitle = "1. Leitura com a Câmera",
    solveModalCameraBody = "Aponte a câmera para cada uma das 6 faces do cubo 3×3 para reconhecimento automático dos adesivos.",
    solveModalManualTitle = "2. Preenchimento Manual",
    solveModalManualBody = "Toque nas peças do cubo virtual para pintar as cores exatamente como estão no seu cubo físico.",
    solveModalTryLearnNow = "Estudar o Método de Camadas Agora",
    solveModalCloseButton = "Entendi",

    settingsModalTitle = "Configurações",
    settingsModalSubtitle = "Personalize idioma, aparência e parâmetros de aprendizado 3D.",
    settingsLanguageSectionTitle = "Idioma do Aplicativo",
    settingsThemeSectionTitle = "Tema Visual",
    settingsThemeDarkLabel = "Escuro Neon",
    settingsThemeLightLabel = "Claro Editorial",
    settingsSpeedSectionTitle = "Velocidade das Animações 3D",
    settingsHighlightPiecesLabel = "Destacar Peças-Alvo na Lição",
    settingsHighlightPiecesSubtitle = "Ilumina as peças importantes da etapa e escurece o restante do cubo.",
    settingsReopenWelcomeButton = "Reabrir Tela de Boas-Vindas e Idioma",
    settingsDoneButton = "Concluir",

    viewportTouchModeLayers = "Camadas",
    viewportTouchModeCamera = "Câmera",
    viewportCameraHint = "Arraste para girar • 2 dedos: zoom e giro",
    viewportRecenterCameraDescription = "Recentrar câmera na visão padrão",
    cubeGestureHintFree = "1 dedo: camada/câmera • 2 dedos: girar 360° e zoom",
    cubeGestureHintCameraOnly = "1 dedo: órbita 360° • 2 dedos: girar e zoom",
    cubeRecenterButton = "Recentrar",
    cubeRecenterContentDesc = "Recentrar câmera na visão padrão",
    cubeDirectionClockwise = "Horário ↻",
    cubeDirectionCounterClockwise = "Anti-horário ↺",
    cubeDirectionDouble180 = "Duplo 180° ⟳",
    cubeViewportAccessibilityDesc = { count -> "Cubo 3D ($count peças)" },

    notationModalTitle = "Guia de Notação 3×3",
    notationModalSubtitle = "Demonstração 3D física em tempo real (F, R, U, L, D, B, M, E, S)",
    notationCloseContentDesc = "Fechar guia de notação",
    notationOrientationBanner = "Toque em uma notação para observar o movimento no cubo.",
    notationRestoreButton = "Restaurar",
    notationRestoreContentDesc = "Restaurar cubo do guia",
    notationRepeatButton = "Repetir",
    notationRepeatContentDesc = "Repetir movimento no cubo 3D",
    notationHowToReadTitle = "Como ler os símbolos",
    notationHowToReadBody = "• Letra sozinha (ex: R, U, F): Giro de 90° no sentido horário.\n" +
        "• Letra com apóstrofo ' (ex: R', U', F'): Giro de 90° no sentido anti-horário.\n" +
        "• Letra com número 2 (ex: R2, U2, B2): Giro duplo de 180° (meia-volta).",
    notationSubClockwise = "Horário",
    notationSubCounterClockwise = "Anti-horário",
    notationSubDouble180 = "180° Duplo",

    exploreTitle = "Explorar Cubo 3D",
    exploreDefaultSubtitle = "Arraste camadas ou gire a câmera livremente",
    exploreBackContentDesc = "Voltar ao início",
    exploreNotationContentDesc = "Guia de Notação",
    exploreStatusSolvedFormat = { count -> "Cubo Resolvido • $count mov." },
    exploreStatusExploringFormat = { count -> "Em exploração • $count mov." },
    exploreLayersTitle = "Camadas",
    exploreModifierNormal = "90°",
    exploreModifierInverse = "Inverso (')",
    exploreModifierDouble = "180° (2)",
    exploreScrambleButton = "Embaralhar",
    exploreUndoButton = "Desfazer",
    exploreCameraButton = "Câmera",
    exploreRestoreButton = "Restaurar",
    exploreFamilyShortLabel = { fam ->
        when (fam) {
            "F" -> "Frente"
            "R" -> "Direita"
            "U" -> "Cima"
            "L" -> "Esq."
            "D" -> "Baixo"
            "B" -> "Trás"
            "M" -> "Meio V"
            "E" -> "Meio H"
            "S" -> "Meio Z"
            else -> fam
        }
    },

    courseTitle = "Método de Camadas",
    courseSubtitle = "As 7 etapas oficiais para resolver o 3×3",
    courseBackContentDesc = "Voltar para a tela inicial",
    courseStudyFlowContentDesc = "Como estudar o curso",
    courseNotationContentDesc = "Abrir Guia de Notação",
    courseProgressFormat = { count -> "Seu Progresso: $count / 7 Etapas" },
    courseStudyFlowTitle = "Fluxo recomendado de estudo",
    courseStudyFlowDismissDesc = "Fechar orientação de estudo",
    courseStudyFlowBody = "1. Escolha uma etapa • 2. Observe os casos no modo Aprender • " +
        "3. Reproduza os movimentos no cubo virtual • 4. Experimente no modo Praticar • " +
        "5. Marque a etapa como concluída quando compreender ou completar o objetivo.",
    courseStepBadgeFormat = { stepNum, casesCount ->
        "ETAPA $stepNum/7 • $casesCount ${if (casesCount == 1) "caso" else "casos"}"
    },
    courseLearnButton = "Aprender",
    coursePracticeButton = "Praticar",
    courseRepeatStepDesc = "Repetir etapa concluída",
    courseResetStageConfirmButton = "Reiniciar etapa",

    lessonBackToCourseDesc = "Voltar ao curso",
    lessonSpeedDesc = "Velocidade da demonstração",
    lessonHighlightPiecesDesc = "Destacar peças importantes",
    lessonNotationGuideDesc = "Guia de Notação",
    lessonLShapeFormedBadge = "Formato de L formado (Trás + Esquerda)",
    lessonDemoCompletedBanner = "Demonstração concluída.",
    lessonGoalAchievedBanner = "Excelente! Você completou esta etapa.",

    lessonPrevCaseDesc = "Caso anterior",
    lessonPrevCaseButton = "Anterior",
    lessonCaseHeader = { current, total -> "Caso $current de $total" },
    lessonNextCaseButton = "Próximo",
    lessonNextCaseDesc = "Próximo caso",
    lessonUnderstandCaseButton = "Entender o caso",
    lessonCaseChip = { num -> "Caso $num" },
    lessonCaseOrientationTip = "Escolha um caso para estudar e toque em 'Entender o caso' para a teoria completa.",
    lessonDismissCaseTipDesc = "Fechar dica sobre casos",

    lessonPlayPhase1Button = "▶ Fase 1 (Linha→L)",
    lessonPlayPhase2Button = "▶ Fase 2 (L→Cruz)",
    lessonPlay2PhasesButton = "▶ 2 Fases",
    lessonPlayPhaseShortButton = { phaseNum -> "▶ F$phaseNum" },
    lessonPlayNPhasesButton = { count -> "▶ $count Fases" },
    lessonCurrentMoveStatus = { idx, total, notation, title ->
        "Mov. $idx/$total: $notation ($title)"
    },
    lessonFirstMoveStatus = { notation, title -> "1º movimento: $notation ($title)" },
    lessonAlgorithmDefaultTitle = "Algoritmo da etapa",
    lessonFullAlgorithm2PhasesTitle = { count -> "Algoritmo Completo ($count mov. • 2 fases):" },
    lessonFullAlgorithm4PhasesTitle = { count -> "Algoritmo Completo ($count mov. • 4 fases):" },
    lessonAlgorithmTitle = { count -> "Algoritmo ($count mov.):" },
    lessonCollapseAlgorithmButton = "Recolher",
    lessonViewAllAlgorithmButton = "Ver todos",
    lessonPhaseTransitionLBadge = "→ L →",
    lessonPhaseTransitionLeftPairBadge = "→ Par Esq. →",
    lessonPhaseTransitionBackPairBadge = "→ Par Trás →",
    lessonPhaseTransition4PairsBadge = "→ 4 Pares →",
    lessonResetDemoDesc = "Reiniciar demonstração",
    lessonPrevMoveDesc = "Movimento anterior",
    lessonPauseButton = "Pausar",
    lessonPlayButton = "Reproduzir",
    lessonNextMoveDesc = "Próximo movimento",
    lessonRepeatSequenceDesc = "Repetir sequência",

    practiceFamilyTitle = { fam -> "Família $fam" },
    practiceHideCompactButton = "Ocultar",
    practiceHideKeyboardButton = "Ocultar teclado",
    practiceKeyboardCompactButton = "Teclado",
    practiceFullKeyboardButton = "Teclado completo",
    practiceToggleKeyboardDesc = "Alternar teclado completo",
    practiceUndoDesc = "Desfazer último movimento",
    practiceResetDesc = "Reiniciar caso prático",
    practiceMoveNormalLabel = "Normal",
    practiceMoveInverseLabel = "Inverso",
    practiceMoveDoubleLabel = "Duplo",

    panelStepGoalPrefix = { goal -> "Objetivo da etapa: $goal" },
    panelTipPrefix = { tip -> "Dica: $tip" },
    panelStepHeader = { stepNum, title -> "Etapa $stepNum: $title" },
    panelGoalPrefix = { goal -> "Objetivo: $goal" },
    panelTeacherTipPrefix = { tip -> "Dica do Professor: $tip" },
    panelNotationMoveExplanation = { notation, explanation -> "Movimento $notation: $explanation" },

    caseModalStepHeader = { stepNum, title -> "ETAPA $stepNum/7 • $title" },
    caseModalTitleHeader = { caseNum, totalCases, title -> "Caso $caseNum de $totalCases — $title" },
    caseModalCloseDesc = "Fechar explicação do caso",
    caseModalWhatHappensTitle = "O que acontece neste caso",
    caseModalTeacherTipTitle = "Dica do Professor",
    caseModalFullAlgorithmTitle = { count -> "Algoritmo Completo ($count movimentos)" },
    caseModalPhaseAlgorithmPrefix = { alg -> "Algoritmo da fase: $alg" },
    caseModalMoveStepHeader = { idx, notation, title -> "Passo $idx: $notation — $title" },
    caseModalStepGoalTitle = { stepNum -> "Objetivo e Teoria da Etapa $stepNum" },
    caseModalOrientationTipTitle = "Nota Pedagógica e Orientação",
    caseModalReturnToCubeButton = "Voltar para o cubo 3D",

    stage6HeadlightsOrangeLabel = "Laranja",
    stage6HeadlightsGreenLabel = "Verde",
    stage6HeadlightsCase1FaceTitle = "Face Traseira (B) no Caso 1: par Laranja",
    stage6HeadlightsCase2FaceTitle = "Exemplo após a Fase 1 do Caso 2: par Verde",
    stage6HeadlightsGuideTitle = "Como identificar um par ('faróis') na Etapa 6",
    stage6HeadlightsTopYellowCaption = "Topo (U): os 4 adesivos amarelos já estão para cima",
    stage6HeadlightsFirstCornerBadge = "1º=L",
    stage6HeadlightsSecondCornerBadge = "2º=L",
    stage6HeadlightsDiagramCaption = { pairColor, centerColor ->
        "Compare APENAS os 2 cantos superiores desta face lateral: ambos são $pairColor ('faróis'!), mesmo que o meio entre eles (×) e o centro (C = $centerColor) tenham outras cores."
    },
    stage6HeadlightsSixRules = listOf(
        "1. Cada peça de canto possui três cores, e cada combinação é única no cubo 3×3.",
        "2. Não estamos procurando duas peças com as mesmas três cores.",
        "3. Na Etapa 6, o amarelo já está no topo para todos os quatro cantos (resolvido na Etapa 5).",
        "4. Ao observar uma face lateral, você deve comparar apenas os dois adesivos superiores das pontas daquela face.",
        "5. Se esses dois adesivos tiverem a mesma cor, isso forma um par (chamado de 'faróis').",
        "6. Eles continuam sendo um par mesmo que ainda não coincidam com o centro da face e mesmo que a peça do meio entre eles seja de outra cor."
    ),
    stage6HeadlightsExamplesTitle = "Exemplo prático nos Casos 1 e 2:",
    stage6HeadlightsExampleCase1 = "• No Caso 1, os dois cantos superiores da face traseira têm adesivos laranja (uma peça é amarelo-laranja-azul e a outra é amarelo-laranja-verde). Embora o centro traseiro seja verde, esses dois adesivos laranja formam o par que deve ficar atrás (B).",
    stage6HeadlightsExampleCase2 = "• No Caso 2, inicialmente nenhuma face lateral tem dois adesivos superiores iguais. Aplicamos o algoritmo uma vez (Fase 1) para criar um par verde na Esquerda (L), giramos U (Fase 2) para levá-lo para Trás (B) e aplicamos o algoritmo novamente (Fase 3).",

    footerPrevStepDesc = "Etapa anterior",
    footerPrevStepButton = "Anterior",
    footerCompleteStepDesc = "Concluir etapa",
    footerCompletedStepButton = "Concluída",
    footerCompleteStepButton = "Concluir",
    footerNextStepButton = "Próxima",
    footerNextStepDesc = "Próxima etapa",

    feedbackDemoCompleted = "Demonstração concluída.",
    feedbackMoveProgressFormat = { idx, total, notation, title ->
        "Movimento $idx/$total: $notation — $title"
    },
    feedbackStepRevertedFormat = { notation -> "Passo revertido: $notation" },
    feedbackStageCompleted = "Excelente! Você completou esta etapa.",
    feedbackMoveExecutedFormat = { notation -> "Movimento executado: $notation" },
    feedbackMoveUndone = "Movimento desfeito",
    feedbackStageMarkedCompletedFormat = { stepNum ->
        "Etapa $stepNum marcada manualmente como concluída."
    },
    feedbackExploreMoveFormat = { notation, title -> "Movimento $notation: $title" },
    feedbackExploreCubeSolved = "Cubo Mágico resolvido!",
    feedbackExploreUndoFormat = { notation -> "Desfeito: $notation" },
    feedbackExploreScrambled = "Cubo embaralhado com 20 movimentos",
    feedbackExploreRestored = "Cubo restaurado ao estado resolvido"
)

val EnUsStrings = AppStrings(
    language = AppLanguage.EN_US,
    appName = OFFICIAL_APP_NAME,
    homeSubtitle = "Learn, explore, and master the 3×3 Rubik's Cube step by step.",
    interactiveCubeBadge = "Interactive 3×3 Cube",
    restoreCubeButton = "Restore",
    settingsButton = "Settings",

    openingBadge = "LAYER-BY-LAYER METHOD • INTERACTIVE 3D",
    openingSubtitle = "Master the 3×3 cube with real-time 3D visualization, guided algorithms, and smart practice.",
    openingFeatureLearn = "7 Guided Stages",
    openingFeatureExplore = "360° Simulator",
    openingFeatureSolve = "26 Real Cases",
    openingContinueButton = "Enter Experience",
    openingVersionLabel = "3D Educational Edition • OpenGL ES 2.0",

    welcomeBadge = "WELCOME • BEM-VINDO",
    welcomeTitle = "Welcome to Cube Master 3D",
    welcomeSubtitle = "Select how you want to view the Cube Master 3D interface. You can switch languages anytime from the main menu.",
    welcomeFeatureInteractive = "Real-Time 360° 3D Cube",
    welcomeFeatureMethod = "Step-by-Step Layer Method",
    welcomeSelectLanguageTitle = "App Language / Idioma do Aplicativo",
    welcomeSelectLanguageHint = "Your preference is saved automatically on this device.",
    welcomeGetStartedButton = "Get Started",

    solveMyCubeTitle = "Solve My Cube",
    solveMyCubeStage3Badge = "Stage 3 • Coming Soon",
    solveMyCubeDescription = "Enter your physical cube's state to get a personalized step-by-step solution.",
    solveWithCameraLabel = "Solve with Camera",
    solveManuallyLabel = "Solve Manually",

    studentProgressTitle = "Course Progress",
    stagesCompletedLabel = { count -> "$count of 7 stages completed" },
    continueStageFormat = { num, title -> "Continue: Stage $num — $title" },
    startFromStage1Label = "Start at Stage 1: White Cross",
    startLearningButton = "Start learning",
    continueButton = "Continue Course",
    practiceButton = "Practice",
    exploreCubeButton = "Explore Cube",
    notationGuideButton = "Notation Guide",

    solveModalBadge = "ROADMAP STAGE 3 FEATURE",
    solveModalTitle = "Solve My Physical Cube",
    solveModalDescription = "In Stage 3 of the Cube Master 3D roadmap, you will be able to input your physical cube's colors to generate a guided step-by-step solution:",
    solveModalCameraTitle = "1. Camera Scanner",
    solveModalCameraBody = "Point your camera at each of the 6 faces of your 3×3 cube for automatic sticker recognition.",
    solveModalManualTitle = "2. Manual Color Picker",
    solveModalManualBody = "Tap the virtual cube stickers to paint the exact color arrangement of your physical cube.",
    solveModalTryLearnNow = "Study the Layer Method Now",
    solveModalCloseButton = "Got It",

    settingsModalTitle = "Settings",
    settingsModalSubtitle = "Customize language, visual theme, and 3D learning preferences.",
    settingsLanguageSectionTitle = "App Language",
    settingsThemeSectionTitle = "Visual Theme",
    settingsThemeDarkLabel = "Neon Dark",
    settingsThemeLightLabel = "Editorial Light",
    settingsSpeedSectionTitle = "3D Animation Speed",
    settingsHighlightPiecesLabel = "Highlight Target Pieces in Lessons",
    settingsHighlightPiecesSubtitle = "Illuminates key pieces for the current step and dims the rest of the cube.",
    settingsReopenWelcomeButton = "Reopen Welcome & Language Screen",
    settingsDoneButton = "Done",

    viewportTouchModeLayers = "Layers",
    viewportTouchModeCamera = "Camera",
    viewportCameraHint = "Drag to orbit • 2 fingers: zoom & twist",
    viewportRecenterCameraDescription = "Recenter camera to default view",
    cubeGestureHintFree = "1 finger: layer/camera • 2 fingers: 360° twist & zoom",
    cubeGestureHintCameraOnly = "1 finger: 360° orbit • 2 fingers: twist & zoom",
    cubeRecenterButton = "Recenter",
    cubeRecenterContentDesc = "Recenter camera to default view",
    cubeDirectionClockwise = "Clockwise ↻",
    cubeDirectionCounterClockwise = "Counter-clockwise ↺",
    cubeDirectionDouble180 = "Double 180° ⟳",
    cubeViewportAccessibilityDesc = { count -> "3D Cube ($count pieces)" },

    notationModalTitle = "3×3 Notation Guide",
    notationModalSubtitle = "Real-time physical 3D demonstration (F, R, U, L, D, B, M, E, S)",
    notationCloseContentDesc = "Close notation guide",
    notationOrientationBanner = "Tap any notation below to watch the move on the 3D cube.",
    notationRestoreButton = "Reset",
    notationRestoreContentDesc = "Reset guide cube",
    notationRepeatButton = "Replay",
    notationRepeatContentDesc = "Replay move on 3D cube",
    notationHowToReadTitle = "How to read notation symbols",
    notationHowToReadBody = "• Single letter (e.g., R, U, F): 90° clockwise turn.\n" +
        "• Letter with apostrophe ' (e.g., R', U', F'): 90° counter-clockwise turn.\n" +
        "• Letter with number 2 (e.g., R2, U2, B2): 180° double turn (half-turn).",
    notationSubClockwise = "Clockwise",
    notationSubCounterClockwise = "Counter-CW",
    notationSubDouble180 = "180° Double",

    exploreTitle = "Explore 3D Cube",
    exploreDefaultSubtitle = "Swipe layers or orbit the camera freely",
    exploreBackContentDesc = "Back to home",
    exploreNotationContentDesc = "Notation Guide",
    exploreStatusSolvedFormat = { count -> "Cube Solved • $count moves" },
    exploreStatusExploringFormat = { count -> "Exploring • $count moves" },
    exploreLayersTitle = "Layers",
    exploreModifierNormal = "90°",
    exploreModifierInverse = "Inverse (')",
    exploreModifierDouble = "180° (2)",
    exploreScrambleButton = "Scramble",
    exploreUndoButton = "Undo",
    exploreCameraButton = "Camera",
    exploreRestoreButton = "Reset",
    exploreFamilyShortLabel = { fam ->
        when (fam) {
            "F" -> "Front"
            "R" -> "Right"
            "U" -> "Up"
            "L" -> "Left"
            "D" -> "Down"
            "B" -> "Back"
            "M" -> "Mid V"
            "E" -> "Mid H"
            "S" -> "Mid Z"
            else -> fam
        }
    },

    courseTitle = "Layer-by-Layer Method",
    courseSubtitle = "The 7 official stages to solve the 3×3 cube",
    courseBackContentDesc = "Back to home screen",
    courseStudyFlowContentDesc = "How to study the course",
    courseNotationContentDesc = "Open Notation Guide",
    courseProgressFormat = { count -> "Your Progress: $count / 7 Stages" },
    courseStudyFlowTitle = "Recommended study flow",
    courseStudyFlowDismissDesc = "Dismiss study flow guide",
    courseStudyFlowBody = "1. Pick a stage • 2. Watch the cases in Learn mode • " +
        "3. Reproduce the moves on the virtual cube • 4. Try it out in Practice mode • " +
        "5. Mark the stage as completed once you master its goal.",
    courseStepBadgeFormat = { stepNum, casesCount ->
        "STAGE $stepNum/7 • $casesCount ${if (casesCount == 1) "case" else "cases"}"
    },
    courseLearnButton = "Learn",
    coursePracticeButton = "Practice",
    courseRepeatStepDesc = "Repeat completed stage",
    courseResetStageConfirmButton = "Reset stage",

    lessonBackToCourseDesc = "Back to course",
    lessonSpeedDesc = "Demonstration speed",
    lessonHighlightPiecesDesc = "Highlight key pieces",
    lessonNotationGuideDesc = "Notation Guide",
    lessonLShapeFormedBadge = "L-Shape formed (Back + Left)",
    lessonDemoCompletedBanner = "Demonstration completed.",
    lessonGoalAchievedBanner = "Excellent! You completed this stage.",

    lessonPrevCaseDesc = "Previous case",
    lessonPrevCaseButton = "Previous",
    lessonCaseHeader = { current, total -> "Case $current of $total" },
    lessonNextCaseButton = "Next",
    lessonNextCaseDesc = "Next case",
    lessonUnderstandCaseButton = "Understand case",
    lessonCaseChip = { num -> "Case $num" },
    lessonCaseOrientationTip = "Choose a case to study and tap 'Understand case' for the full theory.",
    lessonDismissCaseTipDesc = "Dismiss cases tip",

    lessonPlayPhase1Button = "▶ Phase 1 (Line→L)",
    lessonPlayPhase2Button = "▶ Phase 2 (L→Cross)",
    lessonPlay2PhasesButton = "▶ 2 Phases",
    lessonPlayPhaseShortButton = { phaseNum -> "▶ P$phaseNum" },
    lessonPlayNPhasesButton = { count -> "▶ $count Phases" },
    lessonCurrentMoveStatus = { idx, total, notation, title ->
        "Move $idx/$total: $notation ($title)"
    },
    lessonFirstMoveStatus = { notation, title -> "1st move: $notation ($title)" },
    lessonAlgorithmDefaultTitle = "Stage algorithm",
    lessonFullAlgorithm2PhasesTitle = { count -> "Full Algorithm ($count moves • 2 phases):" },
    lessonFullAlgorithm4PhasesTitle = { count -> "Full Algorithm ($count moves • 4 phases):" },
    lessonAlgorithmTitle = { count -> "Algorithm ($count moves):" },
    lessonCollapseAlgorithmButton = "Collapse",
    lessonViewAllAlgorithmButton = "View all",
    lessonPhaseTransitionLBadge = "→ L →",
    lessonPhaseTransitionLeftPairBadge = "→ Left Pair →",
    lessonPhaseTransitionBackPairBadge = "→ Back Pair →",
    lessonPhaseTransition4PairsBadge = "→ 4 Pairs →",
    lessonResetDemoDesc = "Reset demonstration",
    lessonPrevMoveDesc = "Previous move",
    lessonPauseButton = "Pause",
    lessonPlayButton = "Play",
    lessonNextMoveDesc = "Next move",
    lessonRepeatSequenceDesc = "Replay sequence",

    practiceFamilyTitle = { fam -> "Family $fam" },
    practiceHideCompactButton = "Hide",
    practiceHideKeyboardButton = "Hide keyboard",
    practiceKeyboardCompactButton = "Keyboard",
    practiceFullKeyboardButton = "Full keyboard",
    practiceToggleKeyboardDesc = "Toggle full keyboard",
    practiceUndoDesc = "Undo last move",
    practiceResetDesc = "Reset practice case",
    practiceMoveNormalLabel = "Normal",
    practiceMoveInverseLabel = "Inverse",
    practiceMoveDoubleLabel = "Double",

    panelStepGoalPrefix = { goal -> "Step goal: $goal" },
    panelTipPrefix = { tip -> "Tip: $tip" },
    panelStepHeader = { stepNum, title -> "Step $stepNum: $title" },
    panelGoalPrefix = { goal -> "Goal: $goal" },
    panelTeacherTipPrefix = { tip -> "Teacher's Tip: $tip" },
    panelNotationMoveExplanation = { notation, explanation -> "Move $notation: $explanation" },

    caseModalStepHeader = { stepNum, title -> "STEP $stepNum/7 • $title" },
    caseModalTitleHeader = { caseNum, totalCases, title -> "Case $caseNum of $totalCases — $title" },
    caseModalCloseDesc = "Close case explanation",
    caseModalWhatHappensTitle = "What happens in this case",
    caseModalTeacherTipTitle = "Teacher's Tip",
    caseModalFullAlgorithmTitle = { count -> "Full Algorithm ($count moves)" },
    caseModalPhaseAlgorithmPrefix = { alg -> "Phase algorithm: $alg" },
    caseModalMoveStepHeader = { idx, notation, title -> "Step $idx: $notation — $title" },
    caseModalStepGoalTitle = { stepNum -> "Step $stepNum Goal & Theory" },
    caseModalOrientationTipTitle = "Pedagogical & Orientation Note",
    caseModalReturnToCubeButton = "Return to 3D cube",

    stage6HeadlightsOrangeLabel = "Orange",
    stage6HeadlightsGreenLabel = "Green",
    stage6HeadlightsCase1FaceTitle = "Back Face (B) in Case 1: Orange pair",
    stage6HeadlightsCase2FaceTitle = "Example after Phase 1 of Case 2: Green pair",
    stage6HeadlightsGuideTitle = "How to identify a pair ('headlights') in Step 6",
    stage6HeadlightsTopYellowCaption = "Top (U): all 4 yellow stickers already face up",
    stage6HeadlightsFirstCornerBadge = "1st",
    stage6HeadlightsSecondCornerBadge = "2nd",
    stage6HeadlightsDiagramCaption = { pairColor, centerColor ->
        "Compare ONLY the 2 top corners of this side face: both are $pairColor ('headlights'!), even if the middle edge between them (×) and the center (C = $centerColor) have different colors."
    },
    stage6HeadlightsSixRules = listOf(
        "1. Each corner piece has three colors, and each 3-color combination is unique on the 3×3 cube.",
        "2. We are not looking for two pieces with the exact same three colors.",
        "3. In Step 6, yellow is already facing up on all four corners (completed in Step 5).",
        "4. When looking at a side face, compare only the two top outer corner stickers on that face.",
        "5. If those two stickers share the same color, they form a matching pair (called 'headlights').",
        "6. They still count as a pair even if they do not match that face's center yet and even if the middle edge piece between them has a different color."
    ),
    stage6HeadlightsExamplesTitle = "Practical example in Cases 1 and 2:",
    stage6HeadlightsExampleCase1 = "• In Case 1, the two top corners on the back face have orange stickers (one piece is yellow-orange-blue and the other is yellow-orange-green). Even though the back center is green, these two orange stickers form the pair that must be held at the Back (B).",
    stage6HeadlightsExampleCase2 = "• In Case 2, initially no side face has two matching top corner stickers. We apply the algorithm once (Phase 1) to create a green pair on the Left (L), turn U (Phase 2) to move it to the Back (B), and apply the algorithm again (Phase 3).",

    footerPrevStepDesc = "Previous stage",
    footerPrevStepButton = "Previous",
    footerCompleteStepDesc = "Complete stage",
    footerCompletedStepButton = "Completed",
    footerCompleteStepButton = "Complete",
    footerNextStepButton = "Next",
    footerNextStepDesc = "Next stage",

    feedbackDemoCompleted = "Demonstration completed.",
    feedbackMoveProgressFormat = { idx, total, notation, title ->
        "Move $idx/$total: $notation — $title"
    },
    feedbackStepRevertedFormat = { notation -> "Reverted step: $notation" },
    feedbackStageCompleted = "Excellent! You completed this stage.",
    feedbackMoveExecutedFormat = { notation -> "Move executed: $notation" },
    feedbackMoveUndone = "Move undone",
    feedbackStageMarkedCompletedFormat = { stepNum ->
        "Stage $stepNum manually marked as completed."
    },
    feedbackExploreMoveFormat = { notation, title -> "Move $notation: $title" },
    feedbackExploreCubeSolved = "Rubik's Cube solved!",
    feedbackExploreUndoFormat = { notation -> "Undone: $notation" },
    feedbackExploreScrambled = "Cube scrambled with 20 moves",
    feedbackExploreRestored = "Cube restored to solved state"
)

fun stringsForLanguage(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.EN_US -> EnUsStrings
    AppLanguage.PT_BR -> PtBrStrings
}

val LocalAppStrings = staticCompositionLocalOf { PtBrStrings }
