package com.example.curriculum

import com.example.model.CubeColor
import com.example.model.CubeFace
import com.example.model.CubeMove
import com.example.model.CubeState
import com.example.model.Cubie
import com.example.model.CubieType

/**
 * Define quais peças devem receber foco visual em cada etapa pedagógica,
 * reproduzindo de forma trativa o destaque das peças importantes de camadas.pdf.
 */
enum class HighlightMode(val labelPt: String) {
    ALL_VIVID("Todas as peças"),
    WHITE_CROSS("Cruz Branca e Centros"),
    FIRST_LAYER("1ª Camada (Cantos Brancos)"),
    SECOND_LAYER("2ª Camada (Meios Laterais)"),
    YELLOW_CROSS("Cruz Amarela (Meios do Topo)"),
    YELLOW_L_SHAPE("Formato de L (Arestas Trás e Esquerda)"),
    YELLOW_CORNERS_ORIENT("Orientação dos Cantos Amarelos"),
    YELLOW_CORNERS_PERMUTE("Permutação dos Cantos do Topo"),
    FINAL_EDGES_MINERVA("Meios Finais (Minerva)")
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
    val completionSummaryText: String
) {
    val phaseMoves: List<CubeMove> = CubeMove.parseSequence(algorithmText)
}

/**
 * Representa um caso específico dentro de uma das 7 etapas do método de camadas.
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
    val pedagogicalPhases: List<LessonPedagogicalPhase> = emptyList()
) {
    val situationTitle: String = caseTitle.substringAfter(": ").trim().ifEmpty { caseTitle }

    val primaryMoveList: List<CubeMove> = CubeMove.parseSequence(primaryAlgorithm)

    val fullMoveList: List<CubeMove> = CubeMove.parseSequence(fullResolutionAlgorithm)

    fun createInitialState(): CubeState {
        val base = CubeState.createSolved(whiteOnTop = whiteOnTop)
        // Embaralha apenas as camadas posteriores que ainda não pertencem à etapa atual,
        // garantindo que concluir a etapa atual atinja seu objetivo sem resolver todo o cubo prematuramente.
        val stagePreScramble = when (stepNumber) {
            1 -> "R D R' D R D2 R' D" // Embaralha apenas a camada inferior D (oposta à cruz branca no topo)
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
    val shortGoal: String = "Objetivo: $subtitle."
)

/**
 * Explicação interativa de cada família de notação para o Guia de Notação.
 */
data class NotationGuideGroup(
    val family: String,
    val namePt: String,
    val descriptionPt: String,
    val moves: List<CubeMove>
)

object CurriculumRepository {

    val notationGroups: List<NotationGuideGroup> = listOf(
        NotationGuideGroup(
            family = "F",
            namePt = "Frente (Front)",
            descriptionPt = "Gira a camada que está voltada diretamente para você.",
            moves = listOf(CubeMove.F, CubeMove.F_PRIME, CubeMove.F2)
        ),
        NotationGuideGroup(
            family = "R",
            namePt = "Direita (Right)",
            descriptionPt = "Gira a camada lateral direita. O movimento R sobe pela frente; R' desce pela frente.",
            moves = listOf(CubeMove.R, CubeMove.R_PRIME, CubeMove.R2)
        ),
        NotationGuideGroup(
            family = "U",
            namePt = "Cima (Up)",
            descriptionPt = "Gira a camada superior. O movimento U vai da direita para a esquerda na frente.",
            moves = listOf(CubeMove.U, CubeMove.U_PRIME, CubeMove.U2)
        ),
        NotationGuideGroup(
            family = "L",
            namePt = "Esquerda (Left)",
            descriptionPt = "Gira a camada lateral esquerda. O movimento L desce pela frente; L' sobe pela frente.",
            moves = listOf(CubeMove.L, CubeMove.L_PRIME, CubeMove.L2)
        ),
        NotationGuideGroup(
            family = "D",
            namePt = "Baixo (Down)",
            descriptionPt = "Gira a camada inferior. O movimento D vai da esquerda para a direita na frente.",
            moves = listOf(CubeMove.D, CubeMove.D_PRIME, CubeMove.D2)
        ),
        NotationGuideGroup(
            family = "B",
            namePt = "Trás (Back)",
            descriptionPt = "Gira a camada traseira (oposta à frente). Muito usado em B2 na Etapa 6.",
            moves = listOf(CubeMove.B, CubeMove.B_PRIME, CubeMove.B2)
        ),
        NotationGuideGroup(
            family = "M",
            namePt = "Camada do Meio Vertical (Middle)",
            descriptionPt = "Gira a fatia vertical central entre L e R. No PDF: M desce pela frente e M' sobe pela frente (usado no Minerva, Etapa 7).",
            moves = listOf(CubeMove.M, CubeMove.M_PRIME, CubeMove.M2)
        ),
        NotationGuideGroup(
            family = "E",
            namePt = "Camada Central Horizontal (Equatorial)",
            descriptionPt = "Gira a fileira horizontal central entre U e D. O movimento E vai da esquerda para a direita na frente; E' vai da direita para a esquerda.",
            moves = listOf(CubeMove.E, CubeMove.E_PRIME, CubeMove.E2)
        ),
        NotationGuideGroup(
            family = "S",
            namePt = "Camada Central Lateral (Standing)",
            descriptionPt = "Gira a fatia central entre Frente (F) e Trás (B). O movimento S gira no mesmo sentido horário da face F.",
            moves = listOf(CubeMove.S, CubeMove.S_PRIME, CubeMove.S2)
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
            detailedTheory = "As peças centrais são fixas e determinam a cor de cada face. Os meios (arestas) possuem 2 cores e formam a cruz. Os cantos possuem 3 cores e não entram nesta etapa.",
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
                    pedagogicalNote = "Observe que o adesivo lateral vermelho já coincide com o centro vermelho antes de subir."
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
                    pedagogicalNote = "Quando o branco aponta para a direita na aresta frontal-direita, F' encaixa sem tirar os centros do lugar."
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
                    pedagogicalNote = "Um giro de 180° traz a peça diretamente da base para o topo da cruz."
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
                    pedagogicalNote = "Você pode girar o topo livremente para buscar uma peça, desde que devolva o giro ao final."
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
                    pedagogicalNote = "Sempre que o adesivo branco ficar na lateral, usamos a face vizinha (F') após abrir espaço com U."
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
                    pedagogicalNote = "Este é o clássico desvirador de aresta do Passo 1 do PDF."
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
            detailedTheory = "Deixe a cruz branca pronta na face inferior (D). Procure um canto que tenha branco na camada superior, posicione-o entre os dois centros das suas outras cores e aplique o caso correspondente à direção do adesivo branco.",
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
                    pedagogicalNote = "R sobe a vaga da direita, U captura o canto e R' desce o canto resolvido preservando a cruz branca."
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
                    pedagogicalNote = "Espelho exato do Caso 1: F' levanta a vaga pela frente, U' encaixa o canto e F devolve a cruz."
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
                    pedagogicalNote = "Os 4 primeiros movimentos (R U2 R' U') transformam o Caso 3 no Caso 1; os 3 finais (R U R') concluem o encaixe."
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
            detailedTheory = "Com a 1ª camada pronta embaixo, procure no topo meios que NÃO tenham amarelo. Alinhe a cor da frente com o seu centro e veja se a peça deve descer para a direita ou para a esquerda.",
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
                    pedagogicalNote = "Os 5 primeiros movimentos (U R U R' U') formam o par canto+meio no topo; F' U' F encaixa os dois juntos."
                ),
                LessonCase(
                    id = "s3_c2_pdf_fig",
                    stepNumber = 3,
                    caseTitle = "Caso 2 (Figuras do PDF): Meio na Direita para a Frente",
                    shortBadge = "U' F' U' F U + R U R'",
                    compactSummary = "Meio na direita indo para a frente: execute U' F' U' F U e finalize com R U R'.",
                    description = "O meio laranja-azul está na direita e deve entrar na mesma fresta frontal-direita. Seguindo as setas ilustradas no PDF: U' F' U' F U e encaixe o canto com o Caso 1 do 2º passo (R U R').",
                    primaryAlgorithm = "U' F' U' F U R U R'",
                    fullResolutionAlgorithm = "U' F' U' F U R U R'",
                    setupMovesFromSolved = "R U' R' U' F' U F U",
                    pedagogicalNote = "Esta sequência corresponde exatamente aos desenhos do Caso 2 no PDF, finalizando com o Caso 1 do Passo 2 (R U R').",
                    pdfValidationNote = "Nota de Validação do PDF (camadas.pdf): No texto impresso do Caso 2 consta 'U' L' U' L U + Caso 1 do 2º passo', porém as setas desenhadas nos cubos giram a face frontal-esquerda da ilustração (F' e F). Matematicamente, para encaixar na fresta entre F e R terminando com R U R', a sequência exata das figuras é U' F' U' F U R U R'. Já usando L' e L (fresta esquerda), a finalização correta é F U F' (veja o Caso 2B ao lado)."
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
                    pedagogicalNote = "Quando executamos U' L' U' L U na lateral esquerda real (L), o canto branco é recolocado pela frente com F U F'."
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
                    pedagogicalNote = "Inserir um meio qualquer do topo no lugar da peça invertida faz com que ela suba para a camada superior."
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
            detailedTheory = "Não se preocupe ainda com as cores laterais dos meios do topo. Observe apenas o desenho amarelo formado pelos meios no topo: Curva em L (Caso 1 do PDF), Linha horizontal (Caso 2 do PDF) ou Ponto (Caso 3 do PDF). Todos usam o mesmo algoritmo F U R U' R' F'.",
            highlightMode = HighlightMode.YELLOW_CROSS,
            cases = listOf(
                LessonCase(
                    id = "s4_c1",
                    stepNumber = 4,
                    caseTitle = "Caso 1 (PDF): Formato de L (Trás e Esquerda)",
                    shortBadge = "F U R U' R' F'",
                    compactSummary = "Curva em L apontando para trás e esquerda: faça F U R U' R' F' uma vez.",
                    description = "Dois meios amarelos formam um 'L' apontando para trás (UB) e para a esquerda (UL). Execute F U R U' R' F' uma vez para formar a cruz.",
                    primaryAlgorithm = "F U R U' R' F'",
                    fullResolutionAlgorithm = "F U R U' R' F'",
                    setupMovesFromSolved = "F R U R' U' F'",
                    pedagogicalNote = "Segure sempre a curva em L apontando para as 9 horas (esquerda) e 12 horas (trás) antes de aplicar F U R U' R' F'."
                ),
                LessonCase(
                    id = "s4_c2",
                    stepNumber = 4,
                    caseTitle = "Caso 2 (PDF): Linha Horizontal",
                    shortBadge = "F U R U' R' F' (2x)",
                    compactSummary = "Linha amarela deitada (horizontal): aplique F U R U' R' F' duas vezes seguidas.",
                    description = "Dois meios amarelos formam uma linha horizontal (esquerda-direita). Na Fase 1 (F U R U' R' F'), a linha vira o formato de L (trás e esquerda). Na Fase 2, repetimos F U R U' R' F' sem reiniciar o cubo para completar a cruz amarela.",
                    primaryAlgorithm = "F U R U' R' F'",
                    fullResolutionAlgorithm = "F U R U' R' F' F U R U' R' F'",
                    setupMovesFromSolved = "F R U R' U' F' F R U R' U' F'",
                    pedagogicalNote = "Mantenha a linha deitada (horizontal). O 6º movimento (F') encerra a 1ª execução deixando o formato de L, e o 7º movimento (F) inicia a 2ª execução da mesma fórmula para formar a cruz.",
                    pedagogicalPhases = listOf(
                        LessonPedagogicalPhase(
                            phaseNumber = 1,
                            title = "Fase 1 — Transformar a linha em L",
                            executionBannerText = "Primeira execução: transformar a linha horizontal em formato de L.",
                            algorithmText = "F U R U' R' F'",
                            startMoveIndex = 0,
                            endMoveIndex = 5,
                            completionSummaryText = "Agora temos o formato de L. Vamos repetir o algoritmo para completar a cruz."
                        ),
                        LessonPedagogicalPhase(
                            phaseNumber = 2,
                            title = "Fase 2 — Completar a cruz amarela",
                            executionBannerText = "Segunda execução: completar a cruz amarela.",
                            algorithmText = "F U R U' R' F'",
                            startMoveIndex = 6,
                            endMoveIndex = 11,
                            completionSummaryText = "Cruz amarela concluída após as duas execuções do algoritmo!"
                        )
                    )
                ),
                LessonCase(
                    id = "s4_c3",
                    stepNumber = 4,
                    caseTitle = "Caso 3 (PDF): Apenas o Ponto Central",
                    shortBadge = "Caso 1 -> U' -> Caso 2",
                    compactSummary = "Apenas o ponto central amarelo: aplique a fórmula para criar a linha, gire U' e repita.",
                    description = "Nenhum meio amarelo aponta para cima. Faça F U R U' R' F' (forma a linha), alinhe a linha na horizontal com U' e aplique o Caso 2.",
                    primaryAlgorithm = "F U R U' R' F'",
                    fullResolutionAlgorithm = "F U R U' R' F' U' F U R U' R' F' F U R U' R' F'",
                    setupMovesFromSolved = "F R U R' U' F' F R U R' U' F' U F R U R' U' F'",
                    pedagogicalNote = "O mesmo algoritmo de 6 movimentos resolve todos os 3 casos em sequência: Ponto -> Linha -> L -> Cruz!"
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
            detailedTheory = "Com a cruz amarela pronta, conte quantos cantos já estão com o amarelo para cima (1, 2 ou nenhum). Posicione o cubo conforme o caso e aplique o algoritmo R U R' U R U2 R' (conhecido como Sune).",
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
                    pedagogicalNote = "Pense no desenho do topo como um 'peixe' cuja cabeça aponta para a frente-esquerda."
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
                    pedagogicalNote = "Exatamente como orienta o PDF: 'Ficando só um canto certo de novo, coloque-o à esquerda e faça a mesma coisa.'"
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
                    pedagogicalNote = "Para 2 cantos certos, o segredo é olhar o adesivo amarelo na FRENTE (canto frontal-esquerdo)."
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
                    pedagogicalNote = "Para 0 cantos certos, o adesivo amarelo do canto frontal-esquerdo deve apontar para a ESQUERDA."
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
            goalSimple = "Fazer com que os dois cantos superiores de cada face lateral tenham a mesma cor e fiquem alinhados aos centros.",
            detailedTheory = "Procure nas laterais do topo se existem 2 cantos na mesma face com a mesma cor ('faróis'). Se houver (Caso 1), coloque esse par para TRÁS (B) e aplique o algoritmo. Se não houver (Caso 2), aplique de qualquer lado para criar o par.",
            highlightMode = HighlightMode.YELLOW_CORNERS_PERMUTE,
            cases = listOf(
                LessonCase(
                    id = "s6_c1",
                    stepNumber = 6,
                    caseTitle = "Caso 1: Dois cantos com cor igual (Colocar para Trás)",
                    shortBadge = "R' F R' B2 R F' R' B2 R2",
                    compactSummary = "Par de cantos iguais voltado para trás: execute R' F R' B2 R F' R' B2 R2.",
                    description = "Os dois cantos de trás já possuem a mesma cor (verde). Com eles voltados para trás, execute R' F R' B2 R F' R' B2 R2.",
                    primaryAlgorithm = "R' F R' B2 R F' R' B2 R2",
                    fullResolutionAlgorithm = "R' F R' B2 R F' R' B2 R2",
                    setupMovesFromSolved = "R2 B2 R F R' B2 R F' R",
                    pedagogicalNote = "Os dois movimentos B2 no meio e no fim preservam os cantos de trás e trocam os cantos da frente."
                ),
                LessonCase(
                    id = "s6_c2",
                    stepNumber = 6,
                    caseTitle = "Caso 2: Nenhum par de cantos iguais (Permutação Diagonal)",
                    shortBadge = "Caso 1 -> U -> Caso 1 -> U'",
                    compactSummary = "Sem par de cantos iguais: aplique a fórmula para criar um par, leve-o para trás e repita.",
                    description = "Quando nenhuma lateral tem 2 cantos da mesma cor, aplique o algoritmo uma vez de qualquer posição para cair no Caso 1, coloque o par formado para trás (U) e aplique novamente.",
                    primaryAlgorithm = "R' F R' B2 R F' R' B2 R2",
                    fullResolutionAlgorithm = "R' F R' B2 R F' R' B2 R2 U R' F R' B2 R F' R' B2 R2 U'",
                    setupMovesFromSolved = "U R2 B2 R F R' B2 R F' R U' R2 B2 R F R' B2 R F' R",
                    pedagogicalNote = "Ao final, lembre-se da dica do PDF: pode ser necessário girar U, U' ou U2 para alinhar os cantos com os centros."
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
            detailedTheory = "Chegamos ao último passo! Todos os cantos já estão resolvidos. Se uma face lateral já estiver 100% pronta, deixe-a para TRÁS (B) e veja se os 3 meios restantes devem girar no sentido horário (Caso 1) ou anti-horário (Caso 2).",
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
                    pedagogicalNote = "No Minerva Horário, os dois movimentos da camada superior são U (sentido horário)."
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
                    pedagogicalNote = "A única diferença para o Caso 1 é trocar os dois movimentos U por U'!"
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
                    pedagogicalNote = "Qualquer caso de 4 meios sem par resolvido se reduz ao Caso 1 ou 2 após uma aplicação do Minerva!"
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
                    pedagogicalNote = "Parabéns! Ao concluir esta demonstração ou exercício, o Cubo Mágico 3×3 está 100% resolvido!"
                )
            )
        )
    )

    fun getStep(stepNumber: Int): CurriculumStep =
        steps.firstOrNull { it.number == stepNumber } ?: steps.first()

    /**
     * Determina se uma peça específica (Cubie) é relevante para o modo de destaque selecionado.
     * Peças relevantes brilham com cores vivas; peças secundárias ficam levemente esmaecidas
     * quando o aluno ativa o modo "Destacar Peças-Chave" (igual às figuras pedagógicas do PDF).
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
        HighlightMode.FINAL_EDGES_MINERVA -> true
    }
}
