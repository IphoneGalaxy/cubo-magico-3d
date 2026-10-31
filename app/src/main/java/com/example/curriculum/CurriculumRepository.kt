package com.example.curriculum

import com.example.data.AppLanguage
import com.example.model.CubeColor
import com.example.model.CubeFace
import com.example.model.CubeMove
import com.example.model.CubeState
import com.example.model.Cubie
import com.example.model.CubieType

/**
 * Define quais peças devem receber foco visual em cada etapa pedagógica,
 * reproduzindo de forma interativa o destaque das peças importantes do método de camadas.
 */
enum class HighlightMode(
    val labelPt: String,
    val labelEn: String
) {
    ALL_VIVID("Todas as peças", "All pieces"),
    WHITE_CROSS("Cruz Branca e Centros", "White Cross and Centers"),
    FIRST_LAYER("1ª Camada (Cantos Brancos)", "1st Layer (White Corners)"),
    SECOND_LAYER("2ª Camada (Meios Laterais)", "2nd Layer (Middle Edges)"),
    YELLOW_CROSS("Cruz Amarela (Meios do Topo)", "Yellow Cross (Top Edges)"),
    YELLOW_L_SHAPE("Formato de L (Arestas Trás e Esquerda)", "L-Shape (Back and Left Edges)"),
    YELLOW_CORNERS_ORIENT("Orientação dos Cantos Amarelos", "Yellow Corners Orientation"),
    YELLOW_CORNERS_PERMUTE("Permutação dos Cantos do Topo", "Top Corners Permutation"),
    YELLOW_CORNERS_PAIR_BACK("Par de Cantos Traseiro (Faróis em B)", "Back Corner Pair (Headlights on B)"),
    YELLOW_CORNERS_PAIR_LEFT("Par de Cantos Esquerdo (Faróis em L)", "Left Corner Pair (Headlights on L)"),
    YELLOW_CORNERS_PAIR_RIGHT("Par de Cantos Direito (Faróis em R)", "Right Corner Pair (Headlights on R)"),
    YELLOW_CORNERS_PAIR_FRONT("Par de Cantos Frontal (Faróis em F)", "Front Corner Pair (Headlights on F)"),
    FINAL_EDGES_MINERVA("Meios Finais (Minerva)", "Final Edges (Minerva)");

    fun localizedLabel(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) labelEn else labelPt
}

/**
 * Representa uma fase pedagógica explícita dentro de um caso que executa um algoritmo repetido em etapas
 * (como o Caso 2 da Etapa 4 — Linha Horizontal, que aplica F U R U' R' F' duas vezes seguidas:
 * 1ª execução transforma a Linha em L; 2ª execução transforma o L na Cruz Amarela).
 */
data class LessonPedagogicalPhase(
    val phaseNumber: Int,
    val title: String,
    val executionBannerText: String,
    val algorithmText: String,
    val startMoveIndex: Int,
    val endMoveIndex: Int,
    val completionSummaryText: String,
    val titleEn: String = title,
    val executionBannerTextEn: String = executionBannerText,
    val completionSummaryTextEn: String = completionSummaryText
) {
    val phaseMoves: List<CubeMove> = CubeMove.parseSequence(algorithmText)

    fun localizedTitle(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) titleEn else title

    fun localizedExecutionBannerText(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) executionBannerTextEn else executionBannerText

    fun localizedCompletionSummaryText(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) completionSummaryTextEn else completionSummaryText
}

/**
 * Representa um caso específico dentro de uma das 7 etapas do método de camadas.
 * Mantém um único conjunto de dados matemáticos e algoritmos compartilhados, enquanto
 * fornece textos pedagógicos localizados para `en-US` (base/fallback) e `pt-BR`,
 * estruturados também para futura leitura por voz (Text-to-Speech).
 */
data class LessonCase(
    val id: String,
    val stepNumber: Int,
    val caseTitle: String,
    val shortBadge: String,
    val description: String,
    val primaryAlgorithm: String,
    val fullResolutionAlgorithm: String,
    val setupMovesFromSolved: String,
    val whiteOnTop: Boolean = false,
    val pedagogicalNote: String,
    val pdfValidationNote: String? = null,
    val compactSummary: String = description,
    val pedagogicalPhases: List<LessonPedagogicalPhase> = emptyList(),
    val caseTitleEn: String = caseTitle,
    val shortBadgeEn: String = shortBadge,
    val descriptionEn: String = description,
    val pedagogicalNoteEn: String = pedagogicalNote,
    val pdfValidationNoteEn: String? = pdfValidationNote,
    val compactSummaryEn: String = descriptionEn
) {
    val situationTitle: String = caseTitle.substringAfter(": ").trim().ifEmpty { caseTitle }

    val situationTitleEn: String = caseTitleEn.substringAfter(": ").trim().ifEmpty { caseTitleEn }

    val primaryMoveList: List<CubeMove> = CubeMove.parseSequence(primaryAlgorithm)

    val fullMoveList: List<CubeMove> = CubeMove.parseSequence(fullResolutionAlgorithm)

    fun localizedCaseTitle(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) caseTitleEn else caseTitle

    fun localizedSituationTitle(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) situationTitleEn else situationTitle

    fun localizedShortBadge(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) shortBadgeEn else shortBadge

    fun localizedDescription(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) descriptionEn else description

    fun localizedPedagogicalNote(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) pedagogicalNoteEn else pedagogicalNote

    fun localizedPdfValidationNote(language: AppLanguage): String? =
        if (language == AppLanguage.EN_US) pdfValidationNoteEn else pdfValidationNote

    fun localizedCompactSummary(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) compactSummaryEn else compactSummary

    /**
     * Retorna segmentos ordenados e limpos de narração pedagógica no idioma ativo,
     * prontos para uso futuro por um sintetizador de voz (Android Text-to-Speech).
     */
    fun buildNarrationSegments(language: AppLanguage, step: CurriculumStep): List<String> = buildList {
        add("${step.localizedTitle(language)}. ${localizedCaseTitle(language)}.")
        add(step.localizedGoalSimple(language))
        add(localizedCompactSummary(language))
        add(localizedDescription(language))
        add(localizedPedagogicalNote(language))
        localizedPdfValidationNote(language)?.let { add(it) }
    }

    fun createInitialState(): CubeState {
        val base = CubeState.createSolved(whiteOnTop = whiteOnTop)
        // Embaralha apenas as camadas posteriores que ainda não pertencem à etapa atual,
        // garantindo que concluir a etapa atual atinja seu objetivo sem resolver todo o cubo prematuramente.
        val stagePreScramble = when (stepNumber) {
            1 -> "R' D' R D R' D' R D R' D' R D R' D' R D U R' D' R D R' D' R D U R' D' R D R' D' R D R' D' R D R' D' R D U R' D' R D R' D' R D U R D R' D R D2 R' D" // Mantém as 4 arestas da Cruz Branca prontas para o setup do caso, mas desorienta os 4 cantos brancos e embaralha a base D para não formar uma face branca inteira na Etapa 1
            2 -> "F R U R' U' F' U" // Mantém 1ª camada intacta e desorienta o topo U
            3 -> "F R U R' U' F' U" // Mantém 1ª e 2ª camadas intactas e desorienta o topo U
            4 -> "R U R' U R U2 R'" // Mantém 1ª, 2ª camadas e cruz amarela intactas, desorientando os cantos (Etapa 5)
            5 -> "R' F R' B2 R F' R' B2 R2" // Mantém face amarela orientada, permutando cantos para a Etapa 6
            6 -> "F2 U M' U2 M U F2" // Mantém cantos resolvidos, permutando meios para a Etapa 7 (Minerva)
            else -> "" // Na Etapa 7, concluir o algoritmo resolve 100% do cubo
        }
        return base.applySequence(stagePreScramble).applySequence(setupMovesFromSolved)
    }
}

/**
 * Representa uma das 7 etapas pedagógicas do Método de Camadas.
 */
data class CurriculumStep(
    val number: Int,
    val title: String,
    val subtitle: String,
    val goalSimple: String,
    val detailedTheory: String,
    val highlightMode: HighlightMode,
    val cases: List<LessonCase>,
    val shortGoal: String = "Objetivo: $subtitle.",
    val titleEn: String = title,
    val subtitleEn: String = subtitle,
    val shortGoalEn: String = "Goal: $subtitleEn.",
    val goalSimpleEn: String = goalSimple,
    val detailedTheoryEn: String = detailedTheory
) {
    fun localizedTitle(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) titleEn else title

    fun localizedSubtitle(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) subtitleEn else subtitle

    fun localizedShortGoal(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) shortGoalEn else shortGoal

    fun localizedGoalSimple(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) goalSimpleEn else goalSimple

    fun localizedDetailedTheory(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) detailedTheoryEn else detailedTheory
}

/**
 * Explicação interativa de cada família de notação para o Guia de Notação.
 */
data class NotationGuideGroup(
    val family: String,
    val namePt: String,
    val descriptionPt: String,
    val moves: List<CubeMove>,
    val nameEn: String = namePt,
    val descriptionEn: String = descriptionPt
) {
    fun localizedName(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) nameEn else namePt

    fun localizedDescription(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) descriptionEn else descriptionPt
}

object CurriculumRepository {

    val notationGroups: List<NotationGuideGroup> = listOf(
        NotationGuideGroup(
            family = "F",
            namePt = "Frente (Front)",
            descriptionPt = "Gira a camada que está voltada diretamente para você.",
            moves = listOf(CubeMove.F, CubeMove.F_PRIME, CubeMove.F2),
            nameEn = "Front (F)",
            descriptionEn = "Rotates the face layer pointing directly toward you."
        ),
        NotationGuideGroup(
            family = "R",
            namePt = "Direita (Right)",
            descriptionPt = "Gira a camada lateral direita. O movimento R sobe pela frente; R' desce pela frente.",
            moves = listOf(CubeMove.R, CubeMove.R_PRIME, CubeMove.R2),
            nameEn = "Right (R)",
            descriptionEn = "Rotates the right side layer. R moves upward on the front; R' moves downward on the front."
        ),
        NotationGuideGroup(
            family = "U",
            namePt = "Cima (Up)",
            descriptionPt = "Gira a camada superior. O movimento U vai da direita para a esquerda na frente.",
            moves = listOf(CubeMove.U, CubeMove.U_PRIME, CubeMove.U2),
            nameEn = "Up (U)",
            descriptionEn = "Rotates the top layer. U moves from right to left across the front."
        ),
        NotationGuideGroup(
            family = "L",
            namePt = "Esquerda (Left)",
            descriptionPt = "Gira a camada lateral esquerda. O movimento L desce pela frente; L' sobe pela frente.",
            moves = listOf(CubeMove.L, CubeMove.L_PRIME, CubeMove.L2),
            nameEn = "Left (L)",
            descriptionEn = "Rotates the left side layer. L moves downward on the front; L' moves upward on the front."
        ),
        NotationGuideGroup(
            family = "D",
            namePt = "Baixo (Down)",
            descriptionPt = "Gira a camada inferior. O movimento D vai da esquerda para a direita na frente.",
            moves = listOf(CubeMove.D, CubeMove.D_PRIME, CubeMove.D2),
            nameEn = "Down (D)",
            descriptionEn = "Rotates the bottom layer. D moves from left to right across the front."
        ),
        NotationGuideGroup(
            family = "B",
            namePt = "Trás (Back)",
            descriptionPt = "Gira a camada traseira (oposta à frente). Muito usado em B2 na Etapa 6.",
            moves = listOf(CubeMove.B, CubeMove.B_PRIME, CubeMove.B2),
            nameEn = "Back (B)",
            descriptionEn = "Rotates the back layer (opposite the front). Widely used as B2 in Stage 6."
        ),
        NotationGuideGroup(
            family = "M",
            namePt = "Camada do Meio Vertical (Middle)",
            descriptionPt = "Gira a fatia vertical central entre L e R: M desce pela frente e M' sobe pela frente (usado no algoritmo Minerva, na Etapa 7).",
            moves = listOf(CubeMove.M, CubeMove.M_PRIME, CubeMove.M2),
            nameEn = "Vertical Middle Slice (M)",
            descriptionEn = "Rotates the vertical middle slice between L and R: M goes down on the front and M' goes up (used in the Minerva algorithm in Stage 7)."
        ),
        NotationGuideGroup(
            family = "E",
            namePt = "Camada Central Horizontal (Equatorial)",
            descriptionPt = "Gira a fileira horizontal central entre U e D. O movimento E vai da esquerda para a direita na frente; E' vai da direita para a esquerda.",
            moves = listOf(CubeMove.E, CubeMove.E_PRIME, CubeMove.E2),
            nameEn = "Horizontal Equatorial Slice (E)",
            descriptionEn = "Rotates the horizontal middle row between U and D. E goes from left to right across the front; E' goes from right to left."
        ),
        NotationGuideGroup(
            family = "S",
            namePt = "Camada Central Lateral (Standing)",
            descriptionPt = "Gira a fatia central entre Frente (F) e Trás (B). O movimento S gira no mesmo sentido horário da face F.",
            moves = listOf(CubeMove.S, CubeMove.S_PRIME, CubeMove.S2),
            nameEn = "Standing Middle Slice (S)",
            descriptionEn = "Rotates the middle slice between Front (F) and Back (B). S follows the same clockwise direction as the Front face."
        )
    )

    val steps: List<CurriculumStep> = listOf(
        // =====================================================================
        // ETAPA 1: FAZER A CRUZ BRANCA
        // =====================================================================
        CurriculumStep(
            number = 1,
            title = "Fazer a Cruz Branca",
            subtitle = "Alinhe os 4 meios brancos com os centros laterais",
            shortGoal = "Objetivo: formar a cruz branca.",
            goalSimple = "Formar uma cruz na face branca alinhando a segunda cor de cada meio com o centro da mesma cor.",
            titleEn = "Build the White Cross",
            subtitleEn = "Align the 4 white edges with the side centers",
            shortGoalEn = "Goal: build the white cross.",
            goalSimpleEn = "Form a cross on the white face while aligning the second color of each edge with its matching side center.",
            detailedTheory = "As peças centrais são fixas e determinam a cor de cada face. Os meios (arestas) possuem 2 cores e formam a cruz. Os cantos possuem 3 cores e não entram nesta etapa.",
            detailedTheoryEn = "Center pieces are fixed and define the color of each face. Edges have 2 colors and form the cross. Corners have 3 colors and are not part of this stage.",
            highlightMode = HighlightMode.WHITE_CROSS,
            cases = listOf(
                LessonCase(
                    id = "s1_c1",
                    stepNumber = 1,
                    caseTitle = "Caso 1: Meio na camada intermediária (Direita)",
                    shortBadge = "R",
                    compactSummary = "Meio branco-vermelho alinhado na direita: gire R para subir ao topo.",
                    description = "O meio branco-vermelho já está alinhado com o centro vermelho na direita. Basta girar R para levá-lo ao topo.",
                    primaryAlgorithm = "R",
                    fullResolutionAlgorithm = "R",
                    setupMovesFromSolved = "R'",
                    whiteOnTop = true,
                    pedagogicalNote = "Observe que o adesivo lateral vermelho já coincide com o centro vermelho antes de subir.",
                    caseTitleEn = "Case 1: Edge in the middle layer (Right)",
                    shortBadgeEn = "R",
                    compactSummaryEn = "White-red edge aligned on the right: turn R to bring it to the top.",
                    descriptionEn = "The white-red edge is already aligned with the red center on the right face. Simply turn R to lift it into the top cross.",
                    pedagogicalNoteEn = "Notice how the red side sticker already matches the red center before turning upward."
                ),
                LessonCase(
                    id = "s1_c2",
                    stepNumber = 1,
                    caseTitle = "Caso 2: Meio na camada intermediária (Frente)",
                    shortBadge = "F'",
                    compactSummary = "Meio branco-azul alinhado na frente: gire F' para subir ao topo.",
                    description = "O meio branco-azul está na direita com o azul já alinhado ao centro da frente. Gire F' para subir.",
                    primaryAlgorithm = "F'",
                    fullResolutionAlgorithm = "F'",
                    setupMovesFromSolved = "F",
                    whiteOnTop = true,
                    pedagogicalNote = "Quando o branco aponta para a direita na aresta frontal-direita, F' encaixa sem tirar os centros do lugar.",
                    caseTitleEn = "Case 2: Edge in the middle layer (Front)",
                    shortBadgeEn = "F'",
                    compactSummaryEn = "White-blue edge aligned on the front: turn F' to bring it to the top.",
                    descriptionEn = "The white-blue edge sits in the front-right slot with blue already matching the front center. Turn F' to lift it to the top.",
                    pedagogicalNoteEn = "When the white sticker faces right on the front-right edge, F' inserts it cleanly without disturbing the centers."
                ),
                LessonCase(
                    id = "s1_c3",
                    stepNumber = 1,
                    caseTitle = "Caso 3: Meio na camada de baixo",
                    shortBadge = "R2",
                    compactSummary = "Meio na base com cor lateral alinhada: faça meia-volta R2 até o topo.",
                    description = "O meio está na face de baixo com o branco apontando para baixo e a cor lateral alinhada ao centro. Faça meia-volta R2.",
                    primaryAlgorithm = "R2",
                    fullResolutionAlgorithm = "R2",
                    setupMovesFromSolved = "R2",
                    whiteOnTop = true,
                    pedagogicalNote = "Um giro de 180° traz a peça diretamente da base para o topo da cruz.",
                    caseTitleEn = "Case 3: Edge in the bottom layer",
                    shortBadgeEn = "R2",
                    compactSummaryEn = "Edge on the bottom with side color aligned: turn R2 to bring it to the top.",
                    descriptionEn = "The edge is in the bottom layer with white facing down and its side color matching the center. Perform a half-turn R2.",
                    pedagogicalNoteEn = "A 180° turn brings the edge straight from the bottom layer to its top cross position."
                ),
                LessonCase(
                    id = "s1_c4",
                    stepNumber = 1,
                    caseTitle = "Caso 4: Protegendo a cruz com meia-volta",
                    shortBadge = "U2 L' U2",
                    compactSummary = "Traga a vaga com U2, encaixe o meio com L' e devolva a cruz com U2.",
                    description = "Quando a vaga da cruz está do lado oposto, giramos U2 para trazer a vaga, encaixamos com L' e devolvemos com U2.",
                    primaryAlgorithm = "U2 L' U2",
                    fullResolutionAlgorithm = "U2 L' U2",
                    setupMovesFromSolved = "U2 L U2",
                    whiteOnTop = true,
                    pedagogicalNote = "Você pode girar o topo livremente para buscar uma peça, desde que devolva o giro ao final.",
                    caseTitleEn = "Case 4: Protecting the cross with a half-turn",
                    shortBadgeEn = "U2 L' U2",
                    compactSummaryEn = "Bring the open slot with U2, insert the edge with L', and restore the cross with U2.",
                    descriptionEn = "When the target cross slot is on the opposite side, turn U2 to bring the slot over, insert with L', and turn U2 back.",
                    pedagogicalNoteEn = "You can freely rotate the top layer to catch an edge as long as you undo that top turn afterward."
                ),
                LessonCase(
                    id = "s1_c5",
                    stepNumber = 1,
                    caseTitle = "Caso 5: Meio invertido vindo de baixo",
                    shortBadge = "R U F' U'",
                    compactSummary = "Meio invertido na base: suba com R, abra espaço com U, encaixe F' e volte U'.",
                    description = "O meio está embaixo, mas com as cores trocadas. R leva para o meio, U abre espaço na frente, F' encaixa e U' realinha a cruz.",
                    primaryAlgorithm = "R U F' U'",
                    fullResolutionAlgorithm = "R U F' U'",
                    setupMovesFromSolved = "U F U' R'",
                    whiteOnTop = true,
                    pedagogicalNote = "Sempre que o adesivo branco ficar na lateral, usamos a face vizinha (F') após abrir espaço com U.",
                    caseTitleEn = "Case 5: Flipped edge coming from the bottom",
                    shortBadgeEn = "R U F' U'",
                    compactSummaryEn = "Flipped edge on the bottom: lift with R, make room with U, insert F', and restore U'.",
                    descriptionEn = "The edge is on the bottom with its colors flipped. R brings it to the middle layer, U opens space in front, F' inserts it, and U' realigns the cross.",
                    pedagogicalNoteEn = "Whenever the white sticker ends up on the side, use the neighboring face (F') after making room with U."
                ),
                LessonCase(
                    id = "s1_c6",
                    stepNumber = 1,
                    caseTitle = "Caso 6: Meio invertido no topo",
                    shortBadge = "R' U F' U'",
                    compactSummary = "Meio no topo mas invertido: desça com R' e encaixe com U F' U'.",
                    description = "O meio já está no topo, porém invertido (branco na lateral). R' desce a peça para o meio e U F' U' encaixa corretamente.",
                    primaryAlgorithm = "R' U F' U'",
                    fullResolutionAlgorithm = "R' U F' U'",
                    setupMovesFromSolved = "U F U' R",
                    whiteOnTop = true,
                    pedagogicalNote = "Este é o movimento clássico para desvirar um meio branco que subiu com as cores invertidas na Etapa 1.",
                    caseTitleEn = "Case 6: Flipped edge in the top layer",
                    shortBadgeEn = "R' U F' U'",
                    compactSummaryEn = "Edge in the top layer but flipped: move it down with R' and insert with U F' U'.",
                    descriptionEn = "The edge is already in the top slot, but flipped (white on the side). R' moves it to the middle layer and U F' U' inserts it with the right orientation.",
                    pedagogicalNoteEn = "This is the classic sequence to flip a white edge that reached the top layer with inverted colors in Stage 1."
                )
            )
        ),

        // =====================================================================
        // ETAPA 2: COMPLETAR A PRIMEIRA CAMADA
        // =====================================================================
        CurriculumStep(
            number = 2,
            title = "Completar a 1ª Camada",
            subtitle = "Encaixe os 4 cantos brancos com a cruz embaixo",
            shortGoal = "Objetivo: encaixar os 4 cantos brancos.",
            goalSimple = "Com a cruz branca na parte de baixo, posicionar cada canto branco no topo acima do seu destino e encaixá-lo.",
            titleEn = "Complete the 1st Layer",
            subtitleEn = "Insert the 4 white corners with the cross on the bottom",
            shortGoalEn = "Goal: insert the 4 white corners.",
            goalSimpleEn = "With the white cross on the bottom, position each white corner in the top layer above its target slot and insert it.",
            detailedTheory = "Deixe a cruz branca pronta na face inferior (D). Procure um canto que tenha branco na camada superior, posicione-o entre os dois centros das suas outras cores e aplique o caso correspondente à direção do adesivo branco.",
            detailedTheoryEn = "Keep the completed white cross on the bottom face (D). Find a corner with a white sticker in the top layer, place it above its target slot between the matching side centers, and apply the case for the direction the white sticker faces.",
            highlightMode = HighlightMode.FIRST_LAYER,
            cases = listOf(
                LessonCase(
                    id = "s2_c1",
                    stepNumber = 2,
                    caseTitle = "Caso 1: Branco apontando para a Direita",
                    shortBadge = "R U R'",
                    compactSummary = "Canto no topo com branco para a direita: aplique R U R' para encaixar.",
                    description = "O canto branco-azul-laranja está no topo-frente-direita com o adesivo branco voltado para a direita.",
                    primaryAlgorithm = "R U R'",
                    fullResolutionAlgorithm = "R U R'",
                    setupMovesFromSolved = "R U' R'",
                    pedagogicalNote = "R sobe a vaga da direita, U captura o canto e R' desce o canto resolvido preservando a cruz branca.",
                    caseTitleEn = "Case 1: White sticker facing Right",
                    shortBadgeEn = "R U R'",
                    compactSummaryEn = "Corner on top with white facing right: apply R U R' to insert.",
                    descriptionEn = "The white-blue-orange corner is in the top-front-right position with its white sticker facing right.",
                    pedagogicalNoteEn = "R lifts the right slot, U catches the corner, and R' brings the solved corner down while preserving the white cross."
                ),
                LessonCase(
                    id = "s2_c2",
                    stepNumber = 2,
                    caseTitle = "Caso 2: Branco apontando para a Frente",
                    shortBadge = "F' U' F",
                    compactSummary = "Canto no topo com branco para a frente: aplique F' U' F para encaixar.",
                    description = "O canto branco-azul-laranja está no topo-frente-direita com o adesivo branco voltado para a frente.",
                    primaryAlgorithm = "F' U' F",
                    fullResolutionAlgorithm = "F' U' F",
                    setupMovesFromSolved = "F' U F",
                    pedagogicalNote = "Espelho exato do Caso 1: F' levanta a vaga pela frente, U' encaixa o canto e F devolve a cruz.",
                    caseTitleEn = "Case 2: White sticker facing Front",
                    shortBadgeEn = "F' U' F",
                    compactSummaryEn = "Corner on top with white facing front: apply F' U' F to insert.",
                    descriptionEn = "The white-blue-orange corner is in the top-front-right position with its white sticker facing the front.",
                    pedagogicalNoteEn = "Exact mirror of Case 1: F' lifts the slot from the front, U' catches the corner, and F restores the white cross."
                ),
                LessonCase(
                    id = "s2_c3",
                    stepNumber = 2,
                    caseTitle = "Caso 3: Branco apontando para Cima",
                    shortBadge = "R U2 R' U'",
                    compactSummary = "Branco para cima: faça R U2 R' U' para virar o canto e finalize com R U R'.",
                    description = "Quando o adesivo branco aponta para cima, R U2 R' U' gira o canto para que o branco passe a apontar para a direita (caindo no Caso 1: R U R').",
                    primaryAlgorithm = "R U2 R' U'",
                    fullResolutionAlgorithm = "R U2 R' U' R U R'",
                    setupMovesFromSolved = "R U' R' U R U2 R'",
                    pedagogicalNote = "Os 4 primeiros movimentos (R U2 R' U') transformam o Caso 3 no Caso 1; os 3 finais (R U R') concluem o encaixe.",
                    caseTitleEn = "Case 3: White sticker facing Up",
                    shortBadgeEn = "R U2 R' U'",
                    compactSummaryEn = "White facing up: do R U2 R' U' to twist the corner, then finish with R U R'.",
                    descriptionEn = "When the white sticker points upward, R U2 R' U' rotates the corner so white faces right (reducing it to Case 1: R U R').",
                    pedagogicalNoteEn = "The first 4 moves (R U2 R' U') convert Case 3 into Case 1; the final 3 moves (R U R') complete the insertion."
                )
            )
        ),

        // =====================================================================
        // ETAPA 3: COMPLETAR A SEGUNDA CAMADA
        // =====================================================================
        CurriculumStep(
            number = 3,
            title = "Completar a 2ª Camada",
            subtitle = "Insira os 4 meios da camada intermediária",
            shortGoal = "Objetivo: inserir os 4 meios da 2ª camada.",
            goalSimple = "Procurar meios sem amarelo no topo, alinhar com o centro lateral e inserir para a direita ou esquerda.",
            titleEn = "Complete the 2nd Layer",
            subtitleEn = "Insert the 4 middle-layer edges",
            shortGoalEn = "Goal: insert the 4 middle-layer edges.",
            goalSimpleEn = "Find non-yellow edges in the top layer, align the front sticker with its side center, and insert right or left.",
            detailedTheory = "Com a 1ª camada pronta embaixo, procure no topo meios que NÃO tenham amarelo. Alinhe a cor da frente com o seu centro e veja se a peça deve descer para a direita ou para a esquerda.",
            detailedTheoryEn = "With the 1st layer solved on the bottom, look in the top layer for edges that do NOT have yellow. Align the front sticker with its matching center and check whether the edge belongs in the right or left middle slot.",
            highlightMode = HighlightMode.SECOND_LAYER,
            cases = listOf(
                LessonCase(
                    id = "s3_c1",
                    stepNumber = 3,
                    caseTitle = "Caso 1: Inserir meio da Frente para a Direita",
                    shortBadge = "U R U R' U' + F' U' F",
                    compactSummary = "Meio alinhado na frente indo para a direita: faça U R U R' U' e F' U' F.",
                    description = "O meio azul-laranja está na frente (azul alinhado) e deve descer para a direita. Fazemos U R U R' U' e recolocamos o canto branco com o Caso 2 do 2º passo (F' U' F).",
                    primaryAlgorithm = "U R U R' U' F' U' F",
                    fullResolutionAlgorithm = "U R U R' U' F' U' F",
                    setupMovesFromSolved = "F' U F U R U' R' U'",
                    pedagogicalNote = "Os 5 primeiros movimentos (U R U R' U') formam o par canto+meio no topo; F' U' F encaixa os dois juntos.",
                    caseTitleEn = "Case 1: Insert edge from Front to Right",
                    shortBadgeEn = "U R U R' U' + F' U' F",
                    compactSummaryEn = "Edge aligned in front going right: perform U R U R' U' and F' U' F.",
                    descriptionEn = "The blue-orange edge is aligned on the front face (blue matching) and needs to go into the front-right slot. Apply U R U R' U' and reinsert the white corner using Stage 2 Case 2 (F' U' F).",
                    pedagogicalNoteEn = "The first 5 moves (U R U R' U') pair the corner and edge in the top layer; F' U' F inserts both together."
                ),
                LessonCase(
                    id = "s3_c2_pdf_fig",
                    stepNumber = 3,
                    caseTitle = "Caso 2: Meio na Direita para a Frente",
                    shortBadge = "U' F' U' F U + R U R'",
                    compactSummary = "Meio na direita indo para a frente: execute U' F' U' F U e finalize com R U R'.",
                    description = "O meio laranja-azul está na direita e deve entrar na mesma fresta frontal-direita. Afaste com U', prepare a fresta frontal com F' U' F U e finalize encaixando o canto branco com R U R'.",
                    primaryAlgorithm = "U' F' U' F U R U R'",
                    fullResolutionAlgorithm = "U' F' U' F U R U R'",
                    setupMovesFromSolved = "R U' R' U' F' U F U",
                    pedagogicalNote = "Esta sequência insere o meio na fresta frontal-direita e finaliza devolvendo o canto branco com o mesmo movimento básico da Etapa 2 (R U R').",
                    pdfValidationNote = "Dica de Orientação das Faces: Para encaixar o meio na fresta da direita (entre a frente e a direita) finalizando com R U R', a sequência exata é U' F' U' F U R U R'. Se o meio precisar entrar na fresta da esquerda (entre a frente e a esquerda), utiliza-se a versão espelhada U' L' U' L U F U F' (veja o Caso 2B ao lado).",
                    caseTitleEn = "Case 2: Edge on Right going to Front-Right",
                    shortBadgeEn = "U' F' U' F U + R U R'",
                    compactSummaryEn = "Edge on the right going to front-right: execute U' F' U' F U and finish with R U R'.",
                    descriptionEn = "The orange-blue edge is on the right side and belongs in the front-right slot. Move it away with U', prepare the slot with F' U' F U, and finish by reinserting the white corner with R U R'.",
                    pedagogicalNoteEn = "This sequence inserts the edge into the front-right slot and finishes by returning the white corner with the basic Stage 2 trigger (R U R').",
                    pdfValidationNoteEn = "Face Orientation Tip: To insert the edge into the front-right slot (between Front and Right) finishing with R U R', the exact sequence is U' F' U' F U R U R'. If the edge needs to go into the front-left slot (between Front and Left), use the mirrored sequence U' L' U' L U F U F' (see Case 2B next)."
                ),
                LessonCase(
                    id = "s3_c2_left",
                    stepNumber = 3,
                    caseTitle = "Caso 2B (Notação L' L): Inserir para a Esquerda",
                    shortBadge = "U' L' U' L U + F U F'",
                    compactSummary = "Meio na frente indo para a esquerda: faça U' L' U' L U e finalize com F U F'.",
                    description = "O meio azul-vermelho está alinhado na frente e deve descer para a fresta da esquerda (entre Frente e Esquerda).",
                    primaryAlgorithm = "U' L' U' L U F U F'",
                    fullResolutionAlgorithm = "U' L' U' L U F U F'",
                    setupMovesFromSolved = "F U' F' U' L' U L U",
                    pedagogicalNote = "Quando executamos U' L' U' L U na lateral esquerda real (L), o canto branco é recolocado pela frente com F U F'.",
                    caseTitleEn = "Case 2B (L' L Notation): Insert to the Left",
                    shortBadgeEn = "U' L' U' L U + F U F'",
                    compactSummaryEn = "Edge aligned in front going left: perform U' L' U' L U and finish with F U F'.",
                    descriptionEn = "The blue-red edge is aligned on the front face and needs to go down into the front-left slot (between Front and Left).",
                    pedagogicalNoteEn = "When we execute U' L' U' L U on the left face (L), the white corner is reinserted from the front using F U F'."
                ),
                LessonCase(
                    id = "s3_c3",
                    stepNumber = 3,
                    caseTitle = "Caso 3: Meio da 2ª camada invertido no próprio lugar",
                    shortBadge = "Caso 1 -> U2 -> Caso 1",
                    compactSummary = "Meio preso e invertido: aplique o Caso 1 para retirá-lo, gire U2 e repita o Caso 1.",
                    description = "Se um meio já estiver na 2ª camada mas invertido, aplique o Caso 1 para expulsá-lo ao topo, ajuste com U2 e aplique o Caso 1 novamente.",
                    primaryAlgorithm = "U R U R' U' F' U' F",
                    fullResolutionAlgorithm = "U R U R' U' F' U' F U2 U R U R' U' F' U' F",
                    setupMovesFromSolved = "F' U F U R U' R' U' U2 F' U F U R U' R' U'",
                    pedagogicalNote = "Inserir um meio qualquer do topo no lugar da peça invertida faz com que ela suba para a camada superior.",
                    caseTitleEn = "Case 3: 2nd-layer edge flipped in place",
                    shortBadgeEn = "Case 1 -> U2 -> Case 1",
                    compactSummaryEn = "Trapped flipped edge: apply Case 1 to pop it out, turn U2, and repeat Case 1.",
                    descriptionEn = "If an edge is already in the 2nd layer but flipped, apply Case 1 to eject it into the top layer, align with U2, and apply Case 1 again.",
                    pedagogicalNoteEn = "Inserting any top-layer edge into the flipped piece's slot forces the flipped edge up into the top layer."
                )
            )
        ),

        // =====================================================================
        // ETAPA 4: FORMAR A CRUZ AMARELA
        // =====================================================================
        CurriculumStep(
            number = 4,
            title = "Formar a Cruz Amarela",
            subtitle = "Oriente os 4 meios amarelos na face de cima",
            shortGoal = "Objetivo: formar a cruz amarela no topo.",
            goalSimple = "Fazer os 4 meios da camada superior ficarem com o adesivo amarelo apontando para cima, formando uma cruz amarela.",
            titleEn = "Form the Yellow Cross",
            subtitleEn = "Orient the 4 yellow edges on the top face",
            shortGoalEn = "Goal: form the yellow cross on top.",
            goalSimpleEn = "Orient the 4 top-layer edges so their yellow stickers face upward, forming a yellow cross on top.",
            detailedTheory = "Não se preocupe ainda com as cores laterais dos meios do topo. Observe apenas o desenho amarelo formado pelos meios na face superior: formato de L, linha horizontal ou apenas o ponto central. Todos os casos usam o mesmo algoritmo: F U R U' R' F'.",
            detailedTheoryEn = "Do not worry about the side colors of the top edges yet. Look only at the yellow pattern formed by the edges on the upper face: L-shape, horizontal line, or center dot only. Every case uses the same 6-move algorithm: F U R U' R' F'.",
            highlightMode = HighlightMode.YELLOW_CROSS,
            cases = listOf(
                LessonCase(
                    id = "s4_c1",
                    stepNumber = 4,
                    caseTitle = "Caso 1: Formato de L (Trás e Esquerda)",
                    shortBadge = "F U R U' R' F'",
                    compactSummary = "Curva em L apontando para trás e esquerda: faça F U R U' R' F' uma vez.",
                    description = "Dois meios amarelos formam um 'L' apontando para trás (UB) e para a esquerda (UL). Execute F U R U' R' F' uma vez para formar a cruz.",
                    primaryAlgorithm = "F U R U' R' F'",
                    fullResolutionAlgorithm = "F U R U' R' F'",
                    setupMovesFromSolved = "F R U R' U' F'",
                    pedagogicalNote = "Segure sempre a curva em L apontando para as 9 horas (esquerda) e 12 horas (trás) antes de aplicar F U R U' R' F'.",
                    caseTitleEn = "Case 1: L-Shape (Back and Left)",
                    shortBadgeEn = "F U R U' R' F'",
                    compactSummaryEn = "Yellow L-shape pointing back and left: execute F U R U' R' F' once.",
                    descriptionEn = "Two yellow edges form an 'L' pointing toward the back (UB) and left (UL). Execute F U R U' R' F' once to complete the yellow cross.",
                    pedagogicalNoteEn = "Always hold the L-shape pointing to 9 o'clock (left) and 12 o'clock (back) before applying F U R U' R' F'."
                ),
                LessonCase(
                    id = "s4_c2",
                    stepNumber = 4,
                    caseTitle = "Caso 2: Linha Horizontal",
                    shortBadge = "F U R U' R' F' (2x)",
                    compactSummary = "Linha amarela deitada (horizontal): aplique F U R U' R' F' duas vezes seguidas.",
                    description = "Dois meios amarelos formam uma linha horizontal (esquerda-direita). Na Fase 1 (F U R U' R' F'), a linha vira o formato de L (trás e esquerda). Na Fase 2, repetimos F U R U' R' F' sem reiniciar o cubo para completar a cruz amarela.",
                    primaryAlgorithm = "F U R U' R' F'",
                    fullResolutionAlgorithm = "F U R U' R' F' F U R U' R' F'",
                    setupMovesFromSolved = "F R U R' U' F' F R U R' U' F'",
                    pedagogicalNote = "Mantenha a linha deitada (horizontal). O 6º movimento (F') encerra a 1ª execução deixando o formato de L, e o 7º movimento (F) inicia a 2ª execução da mesma fórmula para formar a cruz.",
                    caseTitleEn = "Case 2: Horizontal Line",
                    shortBadgeEn = "F U R U' R' F' (2x)",
                    compactSummaryEn = "Horizontal yellow line (left-right): apply F U R U' R' F' twice in a row.",
                    descriptionEn = "Two yellow edges form a horizontal line (left-right). In Phase 1 (F U R U' R' F'), the line becomes the L-shape (back and left). In Phase 2, we repeat F U R U' R' F' without resetting the cube to complete the yellow cross.",
                    pedagogicalNoteEn = "Keep the yellow line horizontal (left to right). Move 6 (F') finishes the 1st execution leaving the L-shape, and move 7 (F) starts the 2nd execution of the same formula to build the cross.",
                    pedagogicalPhases = listOf(
                        LessonPedagogicalPhase(
                            phaseNumber = 1,
                            title = "Fase 1 — Transformar a linha em L",
                            executionBannerText = "Primeira execução: transformar a linha horizontal em formato de L.",
                            algorithmText = "F U R U' R' F'",
                            startMoveIndex = 0,
                            endMoveIndex = 5,
                            completionSummaryText = "Agora temos o formato de L. Vamos repetir o algoritmo para completar a cruz.",
                            titleEn = "Phase 1 — Turn the Line into an L-Shape",
                            executionBannerTextEn = "First execution: transform the horizontal line into an L-shape.",
                            completionSummaryTextEn = "Now we have the L-shape. Let's repeat the algorithm to complete the yellow cross."
                        ),
                        LessonPedagogicalPhase(
                            phaseNumber = 2,
                            title = "Fase 2 — Completar a cruz amarela",
                            executionBannerText = "Segunda execução: completar a cruz amarela.",
                            algorithmText = "F U R U' R' F'",
                            startMoveIndex = 6,
                            endMoveIndex = 11,
                            completionSummaryText = "Cruz amarela concluída após as duas execuções do algoritmo!",
                            titleEn = "Phase 2 — Complete the Yellow Cross",
                            executionBannerTextEn = "Second execution: complete the yellow cross.",
                            completionSummaryTextEn = "Yellow cross completed after both executions of the algorithm!"
                        )
                    )
                ),
                LessonCase(
                    id = "s4_c3",
                    stepNumber = 4,
                    caseTitle = "Caso 3: Apenas o Ponto Central",
                    shortBadge = "Caso 1 -> U' -> Caso 2",
                    compactSummary = "Apenas o ponto central amarelo: aplique a fórmula para criar a linha, gire U' e repita.",
                    description = "Nenhum meio amarelo aponta para cima. A 1ª execução de F U R U' R' F' cria uma linha vertical; gire U' para deixá-la na horizontal (Caso 2), repita F U R U' R' F' para formar o L (Caso 1) e aplique mais uma vez para completar a cruz amarela.",
                    primaryAlgorithm = "F U R U' R' F'",
                    fullResolutionAlgorithm = "F U R U' R' F' U' F U R U' R' F' F U R U' R' F'",
                    setupMovesFromSolved = "F R U R' U' F' F R U R' U' F' U F R U R' U' F'",
                    pedagogicalNote = "O mesmo algoritmo de 6 movimentos resolve todos os 3 casos em sequência: Ponto -> Linha (alinhada com U') -> L -> Cruz!",
                    caseTitleEn = "Case 3: Center Dot Only",
                    shortBadgeEn = "Case 1 -> U' -> Case 2",
                    compactSummaryEn = "Only the yellow center dot: apply the formula to create a line, turn U', and repeat.",
                    descriptionEn = "No yellow edges face upward. The 1st execution of F U R U' R' F' creates a vertical line; turn U' to make it horizontal (Case 2), repeat F U R U' R' F' to form the L-shape (Case 1), and apply it once more to complete the yellow cross.",
                    pedagogicalNoteEn = "The same 6-move algorithm solves all 3 cases in sequence: Dot -> Line (aligned with U') -> L-Shape -> Cross!"
                )
            )
        ),

        // =====================================================================
        // ETAPA 5: ORIENTAR OS CANTOS AMARELOS
        // =====================================================================
        CurriculumStep(
            number = 5,
            title = "Orientar os Cantos Amarelos",
            subtitle = "Complete toda a face amarela com o Sune",
            shortGoal = "Objetivo: orientar os 4 cantos amarelos.",
            goalSimple = "Virar os cantos do topo até que todos os 9 adesivos da face superior fiquem amarelos.",
            titleEn = "Orient the Yellow Corners",
            subtitleEn = "Complete the entire yellow face using the Sune",
            shortGoalEn = "Goal: orient the 4 yellow corners.",
            goalSimpleEn = "Twist the top corners using the Sune algorithm until all 9 stickers on the upper face are yellow.",
            detailedTheory = "Com a cruz amarela pronta, conte quantos cantos já estão com o amarelo para cima (1, 2 ou nenhum). Posicione o cubo conforme o caso e aplique o algoritmo R U R' U R U2 R' (conhecido como Sune).",
            detailedTheoryEn = "With the yellow cross ready, count how many corners already have yellow facing up (1, 2, or none). Hold the cube according to the case and apply the R U R' U R U2 R' algorithm (known as Sune).",
            highlightMode = HighlightMode.YELLOW_CORNERS_ORIENT,
            cases = listOf(
                LessonCase(
                    id = "s5_c1a",
                    stepNumber = 5,
                    caseTitle = "Caso 1A: Só 1 canto certo (Amarelo na frente)",
                    shortBadge = "R U R' U R U2 R'",
                    compactSummary = "1 canto amarelo certo na frente-esquerda: aplique o Sune (R U R' U R U2 R').",
                    description = "Coloque o único canto amarelo certo na frente-esquerda (UFL). Com o adesivo amarelo da direita na frente, uma aplicação completa a face!",
                    primaryAlgorithm = "R U R' U R U2 R'",
                    fullResolutionAlgorithm = "R U R' U R U2 R'",
                    setupMovesFromSolved = "R U2 R' U' R U' R'",
                    pedagogicalNote = "Pense no desenho do topo como um 'peixe' cuja cabeça aponta para a frente-esquerda.",
                    caseTitleEn = "Case 1A: Only 1 corner oriented (Yellow in front)",
                    shortBadgeEn = "R U R' U R U2 R'",
                    compactSummaryEn = "1 yellow corner oriented at front-left: apply the Sune (R U R' U R U2 R').",
                    descriptionEn = "Place the single oriented yellow corner at the front-left (UFL). With the front-right corner's yellow sticker facing front, one Sune completes the yellow face!",
                    pedagogicalNoteEn = "Picture the top pattern as a 'fish' whose head points toward the front-left corner."
                ),
                LessonCase(
                    id = "s5_c1b",
                    stepNumber = 5,
                    caseTitle = "Caso 1B: Só 1 canto certo (Repetir colocando à esquerda)",
                    shortBadge = "Sune + U2 + Sune",
                    compactSummary = "1 canto certo precisando de 2 giros: faça o Sune, ajuste com U2 e repita o Sune.",
                    description = "Ficando só um canto certo de novo após a primeira aplicação, coloque esse canto na frente-esquerda (U2) e faça R U R' U R U2 R' mais uma vez.",
                    primaryAlgorithm = "R U R' U R U2 R'",
                    fullResolutionAlgorithm = "R U R' U R U2 R' U2 R U R' U R U2 R'",
                    setupMovesFromSolved = "R U2 R' U' R U' R' U2 R U2 R' U' R U' R'",
                    pedagogicalNote = "Regra prática: quando restar novamente apenas um canto amarelo orientado para cima, posicione-o na frente-esquerda (UFL) e aplique a mesma sequência mais uma vez.",
                    caseTitleEn = "Case 1B: Only 1 corner oriented (Repeat after placing at front-left)",
                    shortBadgeEn = "Sune + U2 + Sune",
                    compactSummaryEn = "1 oriented corner needing two cycles: apply Sune, align with U2, and repeat Sune.",
                    descriptionEn = "When a single oriented corner remains after the first Sune, bring that corner back to the front-left (U2) and execute R U R' U R U2 R' once more.",
                    pedagogicalNoteEn = "Rule of thumb: whenever only one yellow corner is oriented upward, place it at the front-left (UFL) and apply Sune again."
                ),
                LessonCase(
                    id = "s5_c2",
                    stepNumber = 5,
                    caseTitle = "Caso 2: Dois cantos certos",
                    shortBadge = "Amarelo na frente-esquerda",
                    compactSummary = "2 cantos certos: deixe um amarelo na frente-esquerda e aplique o Sune.",
                    description = "Com 2 cantos certos, posicione na frente-esquerda um canto cujo adesivo amarelo esteja na FRENTE. Faça o Caso 1, ajuste U e finalize com o Caso 1.",
                    primaryAlgorithm = "R U R' U R U2 R'",
                    fullResolutionAlgorithm = "R U R' U R U2 R' U R U R' U R U2 R' U2 R U R' U R U2 R'",
                    setupMovesFromSolved = "R U2 R' U' R U' R' U2 R U2 R' U' R U' R' U' R U2 R' U' R U' R'",
                    pedagogicalNote = "Para 2 cantos certos, o segredo é olhar o adesivo amarelo na FRENTE (canto frontal-esquerdo).",
                    caseTitleEn = "Case 2: Two corners oriented",
                    shortBadgeEn = "Yellow at front-left",
                    compactSummaryEn = "2 oriented corners: put a front-facing yellow sticker at front-left and apply Sune.",
                    descriptionEn = "With 2 corners oriented, place an unoriented corner at front-left whose yellow sticker faces FRONT. Apply Sune, adjust with U, and finish with Case 1.",
                    pedagogicalNoteEn = "When 2 corners are oriented, the key is having the front-left corner's yellow sticker facing FRONT."
                ),
                LessonCase(
                    id = "s5_c3",
                    stepNumber = 5,
                    caseTitle = "Caso 3: Nenhum canto certo",
                    shortBadge = "Amarelo na lateral esquerda",
                    compactSummary = "Nenhum canto certo: deixe o amarelo apontando para a esquerda e aplique o Sune.",
                    description = "Sem nenhum canto amarelo para cima, posicione na frente-esquerda um canto com o adesivo amarelo na ESQUERDA (L). Faça o Caso 1, ajuste U e finalize.",
                    primaryAlgorithm = "R U R' U R U2 R'",
                    fullResolutionAlgorithm = "R U R' U R U2 R' U R U R' U R U2 R'",
                    setupMovesFromSolved = "R U2 R' U' R U' R' U' R U2 R' U' R U' R'",
                    pedagogicalNote = "Para 0 cantos certos, o adesivo amarelo do canto frontal-esquerdo deve apontar para a ESQUERDA.",
                    caseTitleEn = "Case 3: No corners oriented",
                    shortBadgeEn = "Yellow on left side",
                    compactSummaryEn = "No corners oriented: put a left-facing yellow sticker at front-left and apply Sune.",
                    descriptionEn = "With zero yellow corners facing up, place a corner at front-left whose yellow sticker faces LEFT (L). Apply Sune, turn U, and apply Sune once more.",
                    pedagogicalNoteEn = "When 0 corners are oriented, the front-left corner's yellow sticker must point to the LEFT."
                )
            )
        ),

        // =====================================================================
        // ETAPA 6: POSICIONAR OS CANTOS DA ÚLTIMA CAMADA
        // =====================================================================
        CurriculumStep(
            number = 6,
            title = "Posicionar os Cantos Finais",
            subtitle = "Permute os cantos do topo formando pares ('faróis')",
            shortGoal = "Objetivo: alinhar os cantos finais.",
            goalSimple = "Fazer com que os dois cantos superiores de cada face lateral tenham a mesma cor ('faróis') e fiquem alinhados aos centros.",
            titleEn = "Position the Final Corners",
            subtitleEn = "Permute the top corners to form matching pairs ('headlights')",
            shortGoalEn = "Goal: align the final corners.",
            goalSimpleEn = "Permute the top corners so both upper corners on each side share the same color ('headlights') and align with the side centers.",
            detailedTheory = "Cada peça de canto possui 3 cores, e cada combinação de 3 cores é única no cubo 3×3 — portanto, NÃO estamos procurando dois cantos com as mesmas três cores. Na Etapa 6, os 4 adesivos amarelos dos cantos já estão voltados para cima (isso foi resolvido na Etapa 5). Para encontrar um par ('faróis'), olhe uma face lateral por vez e compare SOMENTE os dois adesivos dos cantos da fileira superior (ignore o meio do topo, que só será resolvido na Etapa 7). Se esses dois adesivos laterais têm a mesma cor, você encontrou um par! Não importa se essa cor ainda não corresponde ao centro daquela face; o alinhamento com os centros será concluído durante a resolução.",
            detailedTheoryEn = "Every corner piece has 3 colors, and each 3-color combination is unique on a 3×3 cube — so we are NOT looking for two identical corner pieces. In Stage 6, all 4 yellow corner stickers already face upward (completed in Stage 5). To spot a matching pair ('headlights'), inspect one side face at a time and compare ONLY the two upper corner stickers on that face (ignore the middle edge between them, which is solved in Stage 7). If those two side corner stickers share the same color, you found a pair! It does not matter if that color does not match that face's center yet; aligning with the centers happens at the end.",
            highlightMode = HighlightMode.YELLOW_CORNERS_PERMUTE,
            cases = listOf(
                LessonCase(
                    id = "s6_c1",
                    stepNumber = 6,
                    caseTitle = "Caso 1: Dois cantos com cor igual (Colocar para Trás)",
                    shortBadge = "R' F R' B2 R F' R' B2 R2",
                    compactSummary = "Par de cantos iguais ('faróis') atrás (B): execute R' F R' B2 R F' R' B2 R2.",
                    description = "Lembre-se: cada canto possui 3 cores em uma combinação única no cubo 3×3, então não procuramos duas peças com as mesmas três cores. Como os 4 adesivos amarelos já estão voltados para cima (resolvidos na Etapa 5), olhe uma face lateral e compare somente os dois adesivos dos cantos da fileira superior. Se tiverem a mesma cor, formam um par ('faróis'), mesmo que ainda não combinem com o centro dessa face. Exemplo neste Caso 1: na face de TRÁS (B), os dois cantos superiores possuem adesivos laranja — eles formam um par mesmo que o centro traseiro seja verde, as outras cores das duas peças sejam diferentes e as arestas superiores ainda não estejam resolvidas. Com esse par atrás (B), execute R' F R' B2 R F' R' B2 R2.",
                    primaryAlgorithm = "R' F R' B2 R F' R' B2 R2",
                    fullResolutionAlgorithm = "R' F R' B2 R F' R' B2 R2",
                    setupMovesFromSolved = "R2 B2 R F R' B2 R F' R",
                    pedagogicalNote = "Por que os dois cantos de trás formam um par se são peças diferentes? Porque ambos já têm o amarelo em cima (Etapa 5) e o laranja apontando para trás (uma peça é amarelo-laranja-azul e a outra é amarelo-laranja-verde). A sequência mantém o canto frontal-esquerdo (UFL) fixo e cicla os outros 3 cantos (UFR → UBL → UBR), levando o par laranja para a direita (R) e formando pares nas 4 laterais.",
                    pdfValidationNote = "Passo a passo no cubo físico (Caso 1): 1) Mantenha amarelo em cima (U) e branco embaixo (D). 2) Olhe cada lateral e compare apenas os 2 adesivos dos cantos da fileira superior (ignore o meio entre eles e a cor do centro). 3) Achando 2 adesivos da mesma cor na mesma lateral ('faróis'), deixe esse par na face de TRÁS (B) — girando o topo (U, U' ou U2) antes de começar. 4) Sem girar o cubo inteiro nas mãos, execute os 9 movimentos: R' F R' B2 R F' R' B2 R2. 5) Todas as 4 laterais passam a ter pares iguais. 6) Ao final, gire o topo (U, U' ou U2) se necessário para alinhar os 4 pares aos centros laterais (as arestas superiores serão resolvidas na Etapa 7).",
                    caseTitleEn = "Case 1: Two corners with matching side color (Place at Back)",
                    shortBadgeEn = "R' F R' B2 R F' R' B2 R2",
                    compactSummaryEn = "Matching corner pair ('headlights') at the back (B): execute R' F R' B2 R F' R' B2 R2.",
                    descriptionEn = "Remember: every corner has a unique 3-color combination on the 3×3 cube, so we are not looking for two pieces with the same three colors. Since all 4 yellow stickers already face upward (from Stage 5), inspect one side face and compare only the two upper corner stickers. If they share the same color, they form a pair ('headlights'), even if they do not match that face's center yet. Example in Case 1: on the BACK face (B), both upper corners show orange stickers — they form a pair even though the back center is green, their third colors differ, and the top edges are not solved yet. With that pair at the back (B), execute R' F R' B2 R F' R' B2 R2.",
                    pedagogicalNoteEn = "Why do the two back corners form a pair if they are different pieces? Because both already have yellow on top (Stage 5) and orange facing back (one piece is yellow-orange-blue and the other is yellow-orange-green). The sequence keeps the front-left corner (UFL) fixed and cycles the other 3 corners (UFR → UBL → UBR), moving the orange pair to the right (R) and forming pairs on all 4 sides.",
                    pdfValidationNoteEn = "Step-by-step on a physical cube (Case 1): 1) Keep yellow on top (U) and white on bottom (D). 2) Check each side face and compare only the 2 upper corner stickers (ignore the edge between them and the center color). 3) Once you find 2 matching corner stickers on the same side ('headlights'), place that pair at the BACK (B) — turning the top layer (U, U', or U2) before starting. 4) Without rotating the whole cube in your hands, execute the 9 moves: R' F R' B2 R F' R' B2 R2. 5) All 4 side faces now have matching corner pairs. 6) Finally, turn the top layer (U, U', or U2) if needed to align all 4 pairs with the side centers (top edges are solved in Stage 7)."
                ),
                LessonCase(
                    id = "s6_c2",
                    stepNumber = 6,
                    caseTitle = "Caso 2: Nenhum par de cantos iguais (Permutação Diagonal)",
                    shortBadge = "Caso 1 -> U -> Caso 1 -> U'",
                    compactSummary = "Sem par igual: aplique a fórmula p/ criar par na esquerda, gire U e repita.",
                    description = "Para buscar um par ('faróis'), olhamos cada face lateral e comparamos somente os dois adesivos dos cantos da fileira superior (já que os 4 amarelos estão em cima desde a Etapa 5 e cada canto tem uma combinação única de 3 cores). Neste Caso 2, nenhuma das quatro faces laterais possui um par assim inicialmente. Por isso, a 1ª execução do algoritmo serve para criar o primeiro par: Fase 1 — execute R' F R' B2 R F' R' B2 R2 para criar um par na face da ESQUERDA (L, par verde neste exemplo); Fase 2 — gire U para levar esse par da esquerda (L) para TRÁS (B); Fase 3 — repita R' F R' B2 R F' R' B2 R2 para formar pares nas 4 faces; Fase 4 — ajuste o topo (U' neste exemplo; U, U', U2 ou nenhum no cubo físico) para alinhar os cantos aos centros.",
                    primaryAlgorithm = "R' F R' B2 R F' R' B2 R2",
                    fullResolutionAlgorithm = "R' F R' B2 R F' R' B2 R2 U R' F R' B2 R F' R' B2 R2 U'",
                    setupMovesFromSolved = "U R2 B2 R F R' B2 R F' R U' R2 B2 R F R' B2 R F' R",
                    pedagogicalNote = "Confira as 4 laterais antes de começar: em nenhuma delas os 2 cantos superiores têm a mesma cor. Ao aplicar R' F R' B2 R F' R' B2 R2 uma vez (Fase 1), dois cantos com adesivo da mesma cor (verde neste exemplo) passam a ficar juntos na face da ESQUERDA (L), mesmo que o centro da esquerda seja laranja. O giro U (Fase 2) coloca esse par atrás (B), transformando o Caso 2 exatamente no Caso 1!",
                    pdfValidationNote = "Como aplicar no cubo físico (Caso 2): 1) Com os 4 amarelos em cima (Etapa 5), compare os 2 adesivos dos cantos superiores de cada lateral. Se nenhuma das 4 faces laterais tiver duas cores iguais, você está no Caso 2. 2) Na Fase 1, execute R' F R' B2 R F' R' B2 R2 de qualquer lado: o primeiro par ('faróis') surgirá sempre na face da ESQUERDA (L), independentemente de combinar ou não com o centro daquela face. 3) Na Fase 2, gire U para levar esse par da esquerda (L) para TRÁS (B). 4) Na Fase 3, repita R' F R' B2 R F' R' B2 R2 para formar pares nas 4 laterais. 5) Na Fase 4, gire U, U' ou U2 para alinhar os 4 pares aos centros (as arestas ficam para a Etapa 7).",
                    caseTitleEn = "Case 2: No matching corner pairs (Diagonal Swap)",
                    shortBadgeEn = "Case 1 -> U -> Case 1 -> U'",
                    compactSummaryEn = "No matching pair: apply the formula to create a pair on the left, turn U, and repeat.",
                    descriptionEn = "To look for a pair ('headlights'), we inspect each side face and compare only the two upper corner stickers (since all 4 yellow stickers face up from Stage 5 and every corner has a unique 3-color combination). In Case 2, none of the four side faces has a matching pair initially. Therefore, the 1st execution creates the first pair: Phase 1 — execute R' F R' B2 R F' R' B2 R2 to create a pair on the LEFT face (L, green pair in this example); Phase 2 — turn U to move that pair from the left (L) to the BACK (B); Phase 3 — repeat R' F R' B2 R F' R' B2 R2 to form pairs on all 4 sides; Phase 4 — adjust the top layer (U' in this example; U, U', U2, or none on a physical cube) to align the corners with the centers.",
                    pedagogicalNoteEn = "Check all 4 sides before starting: none of them has matching upper corner stickers. Applying R' F R' B2 R F' R' B2 R2 once (Phase 1) brings two matching corner stickers (green in this example) together on the LEFT face (L), even though the left center is orange. Turning U (Phase 2) places that pair at the back (B), reducing Case 2 directly to Case 1!",
                    pdfValidationNoteEn = "How to apply on a physical cube (Case 2): 1) With all 4 yellow stickers on top (Stage 5), compare the 2 upper corner stickers on each side face. If none of the 4 sides has matching corner colors, you have Case 2. 2) In Phase 1, execute R' F R' B2 R F' R' B2 R2 from any side: the first pair ('headlights') will always appear on the LEFT face (L), regardless of whether it matches that center. 3) In Phase 2, turn U to move that pair from the left (L) to the BACK (B). 4) In Phase 3, repeat R' F R' B2 R F' R' B2 R2 to form pairs on all 4 sides. 5) In Phase 4, turn U, U', or U2 to align all 4 pairs with the side centers (edges are solved in Stage 7).",
                    pedagogicalPhases = listOf(
                        LessonPedagogicalPhase(
                            phaseNumber = 1,
                            title = "Fase 1 — Criar um par ('faróis')",
                            executionBannerText = "Sem par inicial: 1ª execução cria um par igual na esquerda (L).",
                            algorithmText = "R' F R' B2 R F' R' B2 R2",
                            startMoveIndex = 0,
                            endMoveIndex = 8,
                            completionSummaryText = "Par verde formado na esquerda (L)! Agora leve esse par para trás (B) com U.",
                            titleEn = "Phase 1 — Create a pair ('headlights')",
                            executionBannerTextEn = "No initial pair: 1st execution creates a matching pair on the left (L).",
                            completionSummaryTextEn = "Green pair formed on the left (L)! Now move that pair to the back (B) with U."
                        ),
                        LessonPedagogicalPhase(
                            phaseNumber = 2,
                            title = "Fase 2 — Posicionar o par atrás",
                            executionBannerText = "Gire o topo com U para levar o par da esquerda (L) para trás (B).",
                            algorithmText = "U",
                            startMoveIndex = 9,
                            endMoveIndex = 9,
                            completionSummaryText = "Par posicionado atrás (B) — igual ao Caso 1! Repita o algoritmo.",
                            titleEn = "Phase 2 — Place the pair at the back",
                            executionBannerTextEn = "Turn the top layer with U to move the pair from the left (L) to the back (B).",
                            completionSummaryTextEn = "Pair positioned at the back (B) — just like Case 1! Repeat the algorithm."
                        ),
                        LessonPedagogicalPhase(
                            phaseNumber = 3,
                            title = "Fase 3 — Posicionar os cantos",
                            executionBannerText = "Com o par atrás (B), 2ª execução forma pares nas 4 laterais.",
                            algorithmText = "R' F R' B2 R F' R' B2 R2",
                            startMoveIndex = 10,
                            endMoveIndex = 18,
                            completionSummaryText = "Pares formados nas 4 faces! Falta alinhar o topo aos centros.",
                            titleEn = "Phase 3 — Position the corners",
                            executionBannerTextEn = "With the pair at the back (B), the 2nd execution forms pairs on all 4 sides.",
                            completionSummaryTextEn = "Pairs formed on all 4 faces! Only aligning the top with the centers remains."
                        ),
                        LessonPedagogicalPhase(
                            phaseNumber = 4,
                            title = "Fase 4 — Conferir e alinhar",
                            executionBannerText = "Ajuste o topo (U' neste exemplo; U, U', U2 ou nenhum no cubo físico).",
                            algorithmText = "U'",
                            startMoveIndex = 19,
                            endMoveIndex = 19,
                            completionSummaryText = "Cantos finais posicionados e alinhados aos 4 centros laterais!",
                            titleEn = "Phase 4 — Check and align",
                            executionBannerTextEn = "Adjust the top layer (U' in this example; U, U', U2, or none on a physical cube).",
                            completionSummaryTextEn = "Final corners positioned and aligned with all 4 side centers!"
                        )
                    )
                )
            )
        ),

        // =====================================================================
        // ETAPA 7: POSICIONAR AS ARESTAS FINAIS (MINERVA)
        // =====================================================================
        CurriculumStep(
            number = 7,
            title = "Posicionar os Meios Finais (Minerva)",
            subtitle = "Permute as arestas do topo e resolva o cubo 3×3!",
            shortGoal = "Objetivo: permutar os meios finais (Minerva).",
            goalSimple = "Ciclar os últimos meios da camada superior usando o algoritmo Minerva até resolver 100% do cubo mágico.",
            titleEn = "Position the Final Edges (Minerva)",
            subtitleEn = "Permute the top edges and solve the 3×3 cube!",
            shortGoalEn = "Goal: permute the final edges (Minerva).",
            goalSimpleEn = "Cycle the remaining top-layer edges using the Minerva algorithm until the 3×3 Rubik's Cube is 100% solved.",
            detailedTheory = "Chegamos ao último passo! Todos os cantos já estão resolvidos. Se uma face lateral já estiver 100% pronta, deixe-a para TRÁS (B) e veja se os 3 meios restantes devem girar no sentido horário (Caso 1) ou anti-horário (Caso 2).",
            detailedTheoryEn = "We reached the final stage! All corners are already solved. If one side face is 100% complete, hold it at the BACK (B) and check whether the 3 remaining edges need to cycle clockwise (Case 1) or counter-clockwise (Case 2).",
            highlightMode = HighlightMode.FINAL_EDGES_MINERVA,
            cases = listOf(
                LessonCase(
                    id = "s7_c1",
                    stepNumber = 7,
                    caseTitle = "Caso 1: Minerva Horário (Meio certo para trás)",
                    shortBadge = "F2 U M' U2 M U F2",
                    compactSummary = "Face pronta atrás e 3 meios em sentido horário: aplique F2 U M' U2 M U F2.",
                    description = "A face de trás (verde) já está pronta. Os 3 meios da frente/laterais precisam girar em sentido horário (Esquerda -> Direita -> Frente -> Esquerda).",
                    primaryAlgorithm = "F2 U M' U2 M U F2",
                    fullResolutionAlgorithm = "F2 U M' U2 M U F2",
                    setupMovesFromSolved = "F2 U' M' U2 M U' F2",
                    pedagogicalNote = "No Minerva Horário, os dois movimentos da camada superior são U (sentido horário).",
                    caseTitleEn = "Case 1: Clockwise Minerva (Solved edge at back)",
                    shortBadgeEn = "F2 U M' U2 M U F2",
                    compactSummaryEn = "Solved face at back and 3 edges cycling clockwise: apply F2 U M' U2 M U F2.",
                    descriptionEn = "The back face (green) is already complete. The 3 remaining edges on the front and sides need to cycle clockwise (Left -> Right -> Front -> Left).",
                    pedagogicalNoteEn = "In Clockwise Minerva, both top-layer turns are U (clockwise)."
                ),
                LessonCase(
                    id = "s7_c2",
                    stepNumber = 7,
                    caseTitle = "Caso 2: Minerva Anti-horário (Meio certo para trás)",
                    shortBadge = "F2 U' M' U2 M U' F2",
                    compactSummary = "Face pronta atrás e 3 meios em sentido anti-horário: aplique F2 U' M' U2 M U' F2.",
                    description = "A face de trás já está pronta, e os 3 meios precisam girar em sentido anti-horário (Frente -> Direita -> Esquerda -> Frente).",
                    primaryAlgorithm = "F2 U' M' U2 M U' F2",
                    fullResolutionAlgorithm = "F2 U' M' U2 M U' F2",
                    setupMovesFromSolved = "F2 U M' U2 M U F2",
                    pedagogicalNote = "A única diferença para o Caso 1 é trocar os dois movimentos U por U'!",
                    caseTitleEn = "Case 2: Counter-Clockwise Minerva (Solved edge at back)",
                    shortBadgeEn = "F2 U' M' U2 M U' F2",
                    compactSummaryEn = "Solved face at back and 3 edges cycling counter-clockwise: apply F2 U' M' U2 M U' F2.",
                    descriptionEn = "The back face is already complete, and the 3 remaining edges need to cycle counter-clockwise (Front -> Right -> Left -> Front).",
                    pedagogicalNoteEn = "The only difference from Case 1 is replacing both U turns with U'!"
                ),
                LessonCase(
                    id = "s7_c3",
                    stepNumber = 7,
                    caseTitle = "Caso 3: Quatro meios trocados (Opostos)",
                    shortBadge = "Minerva -> U' -> Minerva -> U",
                    compactSummary = "4 meios trocados (opostos): aplique o Minerva, leve a face pronta para trás e repita.",
                    description = "Nenhum dos 4 meios está certo (troca oposta). Faça o Caso 1 de qualquer lado, posicione o meio resolvido para trás (U') e finalize com o Caso 1.",
                    primaryAlgorithm = "F2 U M' U2 M U F2",
                    fullResolutionAlgorithm = "F2 U M' U2 M U F2 U' F2 U M' U2 M U F2 U",
                    setupMovesFromSolved = "U' F2 U' M' U2 M U' F2 U F2 U' M' U2 M U' F2",
                    pedagogicalNote = "Qualquer caso de 4 meios sem par resolvido se reduz ao Caso 1 ou 2 após uma aplicação do Minerva!",
                    caseTitleEn = "Case 3: All four edges swapped (Opposite)",
                    shortBadgeEn = "Minerva -> U' -> Minerva -> U",
                    compactSummaryEn = "4 edges swapped (opposite): apply Minerva, move the solved face to the back, and repeat.",
                    descriptionEn = "None of the 4 top edges is solved (opposite swap). Apply Case 1 from any side, turn U' to place the newly solved edge at the back, and finish with Case 1.",
                    pedagogicalNoteEn = "Any 4-edge case with no solved side reduces to Case 1 or Case 2 after one Minerva execution!"
                ),
                LessonCase(
                    id = "s7_c4",
                    stepNumber = 7,
                    caseTitle = "Caso 4: Quatro meios trocados (Adjacentes)",
                    shortBadge = "Minerva -> U -> Minerva -> U'",
                    compactSummary = "4 meios trocados (adjacentes): aplique o Minerva, ajuste o topo com U e repita.",
                    description = "Os meios estão trocados em pares adjacentes. Execute o Caso 1, coloque a face resolvida para trás (U) e execute o Caso 1 novamente.",
                    primaryAlgorithm = "F2 U M' U2 M U F2",
                    fullResolutionAlgorithm = "F2 U M' U2 M U F2 U F2 U M' U2 M U F2 U'",
                    setupMovesFromSolved = "U F2 U' M' U2 M U' F2 U' F2 U' M' U2 M U' F2",
                    pedagogicalNote = "Parabéns! Ao concluir esta demonstração ou exercício, o Cubo Mágico 3×3 está 100% resolvido!",
                    caseTitleEn = "Case 4: All four edges swapped (Adjacent)",
                    shortBadgeEn = "Minerva -> U -> Minerva -> U'",
                    compactSummaryEn = "4 edges swapped (adjacent): apply Minerva, adjust top with U, and repeat.",
                    descriptionEn = "The top edges are swapped in adjacent pairs. Execute Case 1, move the solved face to the back (U), and execute Case 1 again.",
                    pedagogicalNoteEn = "Congratulations! Upon completing this demonstration or exercise, the 3×3 Rubik's Cube is 100% solved!"
                )
            )
        )
    )

    fun getStep(stepNumber: Int): CurriculumStep =
        steps.firstOrNull { it.number == stepNumber } ?: steps.first()

    /**
     * Determina se uma peça específica (Cubie) é relevante para o modo de destaque selecionado.
     * Peças relevantes brilham com cores vivas; peças secundárias ficam levemente esmaecidas
     * quando o aluno ativa o modo "Destacar Peças-Chave".
     */
    fun isCubieHighlighted(cubie: Cubie, mode: HighlightMode): Boolean = when (mode) {
        HighlightMode.ALL_VIVID -> true
        HighlightMode.WHITE_CROSS ->
            cubie.type == CubieType.CENTER ||
                (cubie.type == CubieType.EDGE && cubie.hasColor(CubeColor.WHITE))
        HighlightMode.FIRST_LAYER ->
            cubie.type == CubieType.CENTER ||
                cubie.hasColor(CubeColor.WHITE)
        HighlightMode.SECOND_LAYER ->
            cubie.type == CubieType.CENTER ||
                cubie.hasColor(CubeColor.WHITE) ||
                (cubie.type == CubieType.EDGE && !cubie.hasColor(CubeColor.YELLOW))
        HighlightMode.YELLOW_CROSS ->
            cubie.y <= 0 ||
                (cubie.y == 1 && (cubie.type == CubieType.CENTER || cubie.type == CubieType.EDGE))
        HighlightMode.YELLOW_L_SHAPE ->
            (cubie.y == 1 && cubie.type == CubieType.CENTER) ||
                (cubie.y == 1 && cubie.type == CubieType.EDGE && cubie.colorOn(CubeFace.UP) == CubeColor.YELLOW)
        HighlightMode.YELLOW_CORNERS_ORIENT ->
            cubie.y == 1 || cubie.type == CubieType.CENTER
        HighlightMode.YELLOW_CORNERS_PERMUTE ->
            (cubie.y == 1 && cubie.type == CubieType.CORNER) || cubie.type == CubieType.CENTER
        HighlightMode.YELLOW_CORNERS_PAIR_BACK ->
            (cubie.y == 1 && cubie.type == CubieType.CORNER && cubie.z == -1) || cubie.type == CubieType.CENTER
        HighlightMode.YELLOW_CORNERS_PAIR_LEFT ->
            (cubie.y == 1 && cubie.type == CubieType.CORNER && cubie.x == -1) || cubie.type == CubieType.CENTER
        HighlightMode.YELLOW_CORNERS_PAIR_RIGHT ->
            (cubie.y == 1 && cubie.type == CubieType.CORNER && cubie.x == 1) || cubie.type == CubieType.CENTER
        HighlightMode.YELLOW_CORNERS_PAIR_FRONT ->
            (cubie.y == 1 && cubie.type == CubieType.CORNER && cubie.z == 1) || cubie.type == CubieType.CENTER
        HighlightMode.FINAL_EDGES_MINERVA -> true
    }

    /**
     * Determina dinamicamente o modo de destaque visual para a Etapa 6 de acordo com o estado matemático real do cubo:
     * - Quando há exatamente 1 par de cantos iguais ("faróis") em uma lateral (ex.: Trás no início do Caso 1 ou após a Fase 2 do Caso 2,
     *   ou Esquerda após a Fase 1 do Caso 2), destaca especificamente os 2 cantos desse par e os 6 centros.
     * - Quando não há pares (início do Caso 2) ou quando os 4 lados já têm pares formados, destaca os 4 cantos superiores e os 6 centros
     *   (mantendo as 4 arestas superiores esmaecidas, pois pertencem à Etapa 7).
     */
    fun resolveStage6DynamicHighlightMode(cubeState: CubeState): HighlightMode {
        if (!cubeState.isYellowFaceOriented()) return HighlightMode.YELLOW_CORNERS_PERMUTE
        val pairSides = cubeState.findSidesWithCornerPairs()
        return if (pairSides.size == 1) {
            when (pairSides.first()) {
                CubeFace.BACK -> HighlightMode.YELLOW_CORNERS_PAIR_BACK
                CubeFace.LEFT -> HighlightMode.YELLOW_CORNERS_PAIR_LEFT
                CubeFace.RIGHT -> HighlightMode.YELLOW_CORNERS_PAIR_RIGHT
                CubeFace.FRONT -> HighlightMode.YELLOW_CORNERS_PAIR_FRONT
                else -> HighlightMode.YELLOW_CORNERS_PERMUTE
            }
        } else {
            HighlightMode.YELLOW_CORNERS_PERMUTE
        }
    }

    /**
     * Gera o diagnóstico pedagógico em tempo real dos cantos superiores na Etapa 6 a partir do [cubeState] atual,
     * permitindo que o estudante identifique onde está o par ("faróis" / "headlights"), qual cor ele possui e qual ajuste realizar,
     * no idioma ativo ([language]).
     */
    fun describeStage6CornerState(
        cubeState: CubeState,
        language: AppLanguage = AppLanguage.PT_BR
    ): String {
        val isEn = language == AppLanguage.EN_US
        if (cubeState.isYellowCornersPermuted(requireAlignedWithCenters = true)) {
            return if (isEn) {
                "4 pairs aligned with centers! (Edges: Stage 7)"
            } else {
                "4 pares alinhados aos centros! (Meios: Etapa 7)"
            }
        }
        if (cubeState.isYellowCornersPermuted(requireAlignedWithCenters = false)) {
            val alignMove = cubeState.findTopMoveToAlignPermutedCorners()
            return if (isEn) {
                "4 pairs formed: turn ${alignMove?.notation ?: "U"} to align with centers"
            } else {
                "4 pares formados: gire ${alignMove?.notation ?: "U"} p/ alinhar aos centros"
            }
        }
        if (cubeState.isYellowFaceOriented()) {
            val pairSides = cubeState.findSidesWithCornerPairs()
            if (pairSides.size == 1) {
                val side = pairSides.first()
                val colorLabel = cubeState.cornerPairColorOnSide(side)?.localizedLabel(language) ?: ""
                val toBackMove = cubeState.findTopMoveToPlaceCornerPairOnBack()
                return if (isEn) {
                    when (side) {
                        CubeFace.BACK -> "$colorLabel pair on Back (B) • Ready for 9 moves"
                        CubeFace.LEFT -> "$colorLabel pair on Left (L) • Turn ${toBackMove?.notation ?: "U"} to Back"
                        CubeFace.RIGHT -> "$colorLabel pair on Right (R) • Turn ${toBackMove?.notation ?: "U'"} to Back"
                        CubeFace.FRONT -> "$colorLabel pair on Front (F) • Turn ${toBackMove?.notation ?: "U2"} to Back"
                        else -> "$colorLabel pair identified"
                    }
                } else {
                    when (side) {
                        CubeFace.BACK -> "Par $colorLabel em Trás (B) • Pronto p/ os 9 mov."
                        CubeFace.LEFT -> "Par $colorLabel na Esquerda (L) • Gire ${toBackMove?.notation ?: "U"} p/ Trás"
                        CubeFace.RIGHT -> "Par $colorLabel na Direita (R) • Gire ${toBackMove?.notation ?: "U'"} p/ Trás"
                        CubeFace.FRONT -> "Par $colorLabel na Frente (F) • Gire ${toBackMove?.notation ?: "U2"} p/ Trás"
                        else -> "Par $colorLabel identificado"
                    }
                }
            }
            return if (isEn) {
                "No pairs on sides • 9 moves create pair on Left (L)"
            } else {
                "Nenhum par nas laterais • 9 mov. criam par na Esquerda (L)"
            }
        }
        return if (isEn) {
            "Permuting corners (keep the same front face)"
        } else {
            "Permutando cantos (mantenha a mesma face na frente)"
        }
    }
}
