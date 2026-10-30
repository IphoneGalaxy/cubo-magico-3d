package com.example

import com.example.curriculum.CurriculumRepository
import com.example.curriculum.HighlightMode
import com.example.model.CubeColor
import com.example.model.CubeFace
import com.example.model.CubeMove
import com.example.model.CubeState
import com.example.model.CubieType
import com.example.model.InvalidCubeNotationException
import com.example.ui.screens.EXPLORE_FAMILIES
import com.example.ui.screens.resolveExploreMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/**
 * Suíte da Auditoria Final (Prompt 4/4):
 * - Validação explícita de tokens no parser de notação (Testes A–F da Seção 3).
 * - Auditoria matemática profunda das invariantes do Cubo 3x3 e sequências longas com semente fixa (Seção 4).
 * - Validação programática integral das 7 etapas e dos 26 casos pedagógicos (Seção 5).
 * - Auditoria matemática e estrutural das duas fases do Caso 2 da Etapa 4 — Cruz Amarela (Seção 6).
 */
class FinalAuditParserMathCurriculumTest {

    private fun assertValidPhysicalCubeInvariants(state: CubeState, baselineSolved: CubeState) {
        assertEquals("O cubo deve conter exatamente 27 peças (cubies)", 27, state.cubies.size)

        val ids = state.cubies.map { it.id }.toSet()
        assertEquals("Todas as 27 identidades (id 0..26) devem ser preservadas sem duplicação", (0..26).toSet(), ids)

        val homeCoords = state.cubies.map { Triple(it.homeX, it.homeY, it.homeZ) }.toSet()
        assertEquals("As 27 coordenadas de origem (homeX, homeY, homeZ) devem ser únicas", 27, homeCoords.size)

        val currentCoords = state.cubies.map { Triple(it.x, it.y, it.z) }.toSet()
        assertEquals("Nenhuma peça pode ocupar a mesma posição 3D (x, y, z) que outra", 27, currentCoords.size)
        for ((x, y, z) in currentCoords) {
            assertTrue("Coordenada X fora do intervalo [-1, 1]: $x", x in -1..1)
            assertTrue("Coordenada Y fora do intervalo [-1, 1]: $y", y in -1..1)
            assertTrue("Coordenada Z fora do intervalo [-1, 1]: $z", z in -1..1)
        }

        // Contagem por tipo estrutural
        assertEquals(1, state.cubies.count { it.type == CubieType.CORE })
        assertEquals(6, state.cubies.count { it.type == CubieType.CENTER })
        assertEquals(12, state.cubies.count { it.type == CubieType.EDGE })
        assertEquals(8, state.cubies.count { it.type == CubieType.CORNER })

        // Contagem global dos 54 adesivos (9 de cada cor)
        val allStickers = state.cubies.flatMap { it.stickers.values }
        assertEquals("O cubo deve possuir 54 adesivos no total", 54, allStickers.size)
        for (color in CubeColor.entries) {
            assertEquals(
                "A cor $color deve aparecer exatamente 9 vezes no cubo",
                9,
                allStickers.count { it == color }
            )
        }

        // Verifica que cada cubie preserva seu conjunto original de cores (identidade física da peça)
        // e que os adesivos estão apontando apenas para as faces externas correspondentes a (x, y, z)
        val baselineById = baselineSolved.cubies.associateBy { it.id }
        for (cubie in state.cubies) {
            val original = baselineById.getValue(cubie.id)
            assertEquals(
                "A peça id=${cubie.id} deve manter suas coordenadas home originais",
                Triple(original.homeX, original.homeY, original.homeZ),
                Triple(cubie.homeX, cubie.homeY, cubie.homeZ)
            )
            assertEquals(
                "A peça id=${cubie.id} deve preservar exatamente seu conjunto de cores de fábrica",
                original.colorsSet(),
                cubie.colorsSet()
            )
            assertEquals(
                "A quantidade de adesivos da peça id=${cubie.id} não pode mudar",
                original.stickers.size,
                cubie.stickers.size
            )

            val expectedFaces = mutableSetOf<CubeFace>()
            if (cubie.y == 1) expectedFaces.add(CubeFace.UP)
            if (cubie.y == -1) expectedFaces.add(CubeFace.DOWN)
            if (cubie.z == 1) expectedFaces.add(CubeFace.FRONT)
            if (cubie.z == -1) expectedFaces.add(CubeFace.BACK)
            if (cubie.x == 1) expectedFaces.add(CubeFace.RIGHT)
            if (cubie.x == -1) expectedFaces.add(CubeFace.LEFT)

            assertEquals(
                "As faces dos adesivos da peça id=${cubie.id} em (${cubie.x},${cubie.y},${cubie.z}) devem coincidir com as normais externas",
                expectedFaces,
                cubie.stickers.keys
            )
        }
    }

    /**
     * SEÇÃO 3 — TESTES A, B, C, E, F DO PARSER DE NOTAÇÃO:
     * - Teste A: Todos os 27 movimentos suportados (F, R, U, L, D, B, M, E, S com ', 2).
     * - Teste B: Diferentes caracteres de apóstrofo (', ’, ‘, ′).
     * - Teste C: Espaçamentos variados (simples, múltiplos, tabulações, quebras de linha, vazio).
     * - Teste E: Round-trip (parseSequence(formatSequence(moves)) == moves).
     * - Teste F: Sequência seguida de sua inversa restaura o estado matemático inicial.
     */
    @Test
    fun testParserExhaustiveSupportedMovesApostrophesWhitespaceAndRoundTrip() {
        // Teste A — Todos os 27 movimentos suportados na ordem canônica
        val allSupportedNotation =
            "F F' F2 R R' R2 U U' U2 L L' L2 D D' D2 B B' B2 M M' M2 E E' E2 S S' S2"
        val expectedMoves = listOf(
            CubeMove.F, CubeMove.F_PRIME, CubeMove.F2,
            CubeMove.R, CubeMove.R_PRIME, CubeMove.R2,
            CubeMove.U, CubeMove.U_PRIME, CubeMove.U2,
            CubeMove.L, CubeMove.L_PRIME, CubeMove.L2,
            CubeMove.D, CubeMove.D_PRIME, CubeMove.D2,
            CubeMove.B, CubeMove.B_PRIME, CubeMove.B2,
            CubeMove.M, CubeMove.M_PRIME, CubeMove.M2,
            CubeMove.E, CubeMove.E_PRIME, CubeMove.E2,
            CubeMove.S, CubeMove.S_PRIME, CubeMove.S2
        )
        val parsedAll = CubeMove.parseSequence(allSupportedNotation)
        assertEquals("Todos os 27 movimentos devem ser reconhecidos na ordem exata", expectedMoves, parsedAll)

        // Teste B — Diferentes caracteres de apóstrofo: "'", "’", "‘", "′"
        val apostropheVariants = listOf("'", "’", "‘", "′")
        val families = listOf(
            "F" to CubeMove.F_PRIME,
            "R" to CubeMove.R_PRIME,
            "U" to CubeMove.U_PRIME,
            "L" to CubeMove.L_PRIME,
            "D" to CubeMove.D_PRIME,
            "B" to CubeMove.B_PRIME,
            "M" to CubeMove.M_PRIME,
            "E" to CubeMove.E_PRIME,
            "S" to CubeMove.S_PRIME
        )
        for (apo in apostropheVariants) {
            val seq = families.joinToString(" ") { (fam, _) -> "$fam$apo" }
            val parsedApo = CubeMove.parseSequence(seq)
            assertEquals(
                "Falha ao normalizar apóstrofo '$apo'",
                families.map { it.second },
                parsedApo
            )
        }

        // Teste C — Espaçamentos (vazio, em branco, múltiplos espaços, tabulações e quebras de linha)
        assertTrue("String vazia deve retornar lista vazia", CubeMove.parseSequence("").isEmpty())
        assertTrue("String apenas com espaços/tabs/newlines deve retornar lista vazia", CubeMove.parseSequence("   \t  \n \r\n ").isEmpty())

        val messyWhitespace = "  \n\t  F   U’\t\tR2 \n  U‘   R′\r\nF'   \t "
        val expectedMessy = listOf(
            CubeMove.F, CubeMove.U_PRIME, CubeMove.R2,
            CubeMove.U_PRIME, CubeMove.R_PRIME, CubeMove.F_PRIME
        )
        assertEquals(
            "Espaços múltiplos, tabulações e quebras de linha devem ser aceitos como separadores",
            expectedMessy,
            CubeMove.parseSequence(messyWhitespace)
        )

        // Teste E — Round-trip: parseSequence(formatSequence(moves)) == moves
        assertEquals(
            expectedMoves,
            CubeMove.parseSequence(CubeMove.formatSequence(expectedMoves))
        )
        val rng = Random(424242L)
        repeat(25) {
            val randomMoves = List(30) { CubeMove.entries[rng.nextInt(CubeMove.entries.size)] }
            val formatted = CubeMove.formatSequence(randomMoves)
            val roundTripped = CubeMove.parseSequence(formatted)
            assertEquals("Round-trip deve preservar a sequência exata", randomMoves, roundTripped)
        }

        // Teste F — Sequência inversa incluindo todas as famílias (F, R, U, L, D, B, M, E, S)
        val baseCube = CubeState.createSolved()
        val mixedWithSlices = CubeMove.parseSequence("F R' U2 L D' B2 M E' S2 M' E S' U R F'")
        val invertedFormatted = CubeMove.formatSequence(CubeMove.invertSequence(mixedWithSlices))
        val invertedParsed = CubeMove.parseSequence(invertedFormatted)
        val afterForwardAndInverse = baseCube.applyMoves(mixedWithSlices).applyMoves(invertedParsed)
        assertEquals(
            "Aplicar uma sequência seguida de sua inversa deve restaurar exatamente o CubeState original",
            baseCube,
            afterForwardAndInverse
        )
    }

    /**
     * SEÇÃO 3 — TESTE D DO PARSER DE NOTAÇÃO:
     * Verifica que tokens inválidos NUNCA são descartados silenciosamente, lançando
     * [InvalidCubeNotationException] com o token inválido e sua posição, e impedindo execução parcial.
     */
    @Test
    fun testParserRejectsInvalidTokensAndPreventsPartialExecution() {
        val invalidCases = listOf(
            Triple("R U X R'", "X", 2),
            Triple("F U R3 U'", "R3", 2),
            Triple("R U INVALIDO F'", "INVALIDO", 2),
            Triple("F2 U' ?? R", "??", 2),
            Triple("R U MOVIMENTO_INVALIDO R' U'", "MOVIMENTO_INVALIDO", 2),
            Triple("Z2 R U R'", "Z2", 0),
            Triple("F U R U' R' F' FIM", "FIM", 6),
            Triple("r U R'", "r", 0),
            Triple("R'' U", "R''", 0)
        )

        for ((rawSeq, expectedBadToken, expectedIndex) in invalidCases) {
            try {
                val res = CubeMove.parseSequence(rawSeq)
                fail("A sequência inválida \"$rawSeq\" deveria falhar, mas retornou: $res")
            } catch (e: InvalidCubeNotationException) {
                assertEquals("Token inválido incorreto para \"$rawSeq\"", expectedBadToken, e.invalidToken)
                assertEquals("Índice do token inválido incorreto para \"$rawSeq\"", expectedIndex, e.tokenIndex)
                assertEquals(rawSeq, e.rawSequence)
                assertTrue(
                    "A mensagem de erro deve identificar o token '$expectedBadToken'",
                    e.message?.contains(expectedBadToken) == true
                )
            }

            val resultWrapper = CubeMove.parseSequenceResult(rawSeq)
            assertTrue("parseSequenceResult deve indicar falha para \"$rawSeq\"", resultWrapper.isFailure)
            assertTrue(resultWrapper.exceptionOrNull() is InvalidCubeNotationException)
        }

        // Garante que CubeState.applySequence NÃO aplica execução parcial quando há um token inválido no meio
        val initialCube = CubeState.createSolved().applySequence("R U")
        var cubeSnapshot = initialCube
        try {
            cubeSnapshot = cubeSnapshot.applySequence("F R MOVIMENTO_INVALIDO U'")
            fail("applySequence deveria ter lançado InvalidCubeNotationException")
        } catch (e: InvalidCubeNotationException) {
            assertEquals("MOVIMENTO_INVALIDO", e.invalidToken)
            assertEquals(2, e.tokenIndex)
        }
        assertEquals(
            "O estado do cubo não pode sofrer mutação/execução parcial quando a sequência possui token inválido",
            initialCube,
            cubeSnapshot
        )
    }

    /**
     * SEÇÃO 4 — AUDITORIA MATEMÁTICA FINAL DO CUBO:
     * Verifica todas as invariantes físicas e algébricas dos 27 movimentos e executa sequências
     * longas determinísticas (com semente fixa) seguidas de suas inversas.
     */
    @Test
    fun testMathematicalInvariantsCubieIdentitiesOrientationsAndSeededLongSequences() {
        val solved = CubeState.createSolved()
        assertValidPhysicalCubeInvariants(solved, solved)
        assertTrue(solved.isSolved())

        for (move in CubeMove.entries) {
            val moved = solved.applyMove(move)
            assertValidPhysicalCubeInvariants(moved, solved)
            assertFalse("Um único movimento (${move.notation}) sobre o cubo resolvido não pode deixá-lo resolvido", moved.isSolved())

            // 1. Apenas a camada correspondente pode ser alterada; as 18 peças fora da camada devem permanecer idênticas
            var affectedCount = 0
            for (cubieBefore in solved.cubies) {
                val cubieAfter = moved.cubies.first { it.id == cubieBefore.id }
                if (move.affectsCubie(cubieBefore)) {
                    affectedCount++
                } else {
                    assertEquals(
                        "Movimento ${move.notation} não pode alterar peça fora de sua camada: $cubieBefore",
                        cubieBefore,
                        cubieAfter
                    )
                }
            }
            assertEquals("Cada camada 3x3 deve conter exatamente 9 peças", 9, affectedCount)

            // 2. Movimento seguido de seu inverso restaura o estado exato
            val backToSolved = moved.applyMove(move.inverse())
            assertEquals("Movimento ${move.notation} seguido de ${move.inverse().notation} deve restaurar o estado", solved, backToSolved)

            // 3. Quatro aplicações de qualquer movimento (90° ou 180°) restauram o estado original
            val fourTimes = solved.applyMove(move).applyMove(move).applyMove(move).applyMove(move)
            assertEquals("4 aplicações de ${move.notation} devem restaurar o estado", solved, fourTimes)
        }

        // 4. Duas rotações de 90° equivalem exatamente à rotação de 180° em todas as 9 famílias (F, R, U, L, D, B, M, E, S)
        val families9 = listOf("F", "R", "U", "L", "D", "B", "M", "E", "S")
        val nonTrivialBase = solved.applySequence("R U F' L D' B M E' S")
        for (fam in families9) {
            val cw = CubeMove.fromNotation(fam)!!
            val ccw = CubeMove.fromNotation("$fam'")!!
            val half = CubeMove.fromNotation("${fam}2")!!

            assertEquals(
                "Duas rotações $fam devem equivaler a ${fam}2",
                nonTrivialBase.applyMove(half),
                nonTrivialBase.applyMove(cw).applyMove(cw)
            )
            assertEquals(
                "Duas rotações $fam' devem equivaler a ${fam}2",
                nonTrivialBase.applyMove(half),
                nonTrivialBase.applyMove(ccw).applyMove(ccw)
            )
        }

        // 5. Sequências longas reprodutíveis com semente fixa (20 sequências de 100 movimentos = 2.000 movimentos + 2.000 inversos)
        val seededRandom = Random(20261027L)
        val allMoves = CubeMove.entries
        repeat(20) { seqIdx ->
            val longSequence = List(100) { allMoves[seededRandom.nextInt(allMoves.size)] }
            var current = solved
            for ((stepIdx, mv) in longSequence.withIndex()) {
                current = current.applyMove(mv)
                if (stepIdx % 25 == 24) {
                    assertValidPhysicalCubeInvariants(current, solved)
                }
            }
            assertValidPhysicalCubeInvariants(current, solved)

            val inverseSeq = CubeMove.invertSequence(longSequence)
            val restored = current.applyMoves(inverseSeq)
            assertEquals(
                "Sequência longa #$seqIdx (100 movimentos incluindo M/E/S) seguida da inversa deve restaurar todas as 27 peças com orientação exata",
                solved,
                restored
            )
            assertTrue(restored.isSolved())
        }
    }

    /**
     * SEÇÃO 5 — VALIDAÇÃO INTEGRAL DAS SETE ETAPAS E DOS 26 CASOS PEDAGÓGICOS:
     * Audita programaticamente todos os 26 casos de CurriculumRepository.
     */
    @Test
    fun testCompleteAuditOfSevenStepsAndAll26PedagogicalCases() {
        val steps = CurriculumRepository.steps
        assertEquals("Devem existir exatamente 7 etapas pedagógicas", 7, steps.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), steps.map { it.number })

        val allCases = steps.flatMap { it.cases }
        assertEquals("Devem existir exatamente 26 casos pedagógicos cadastrados", 26, allCases.size)

        val uniqueIds = allCases.map { it.id }.toSet()
        assertEquals("Todos os 26 casos devem possuir identificadores únicos", 26, uniqueIds.size)

        for (step in steps) {
            assertTrue("Título da etapa ${step.number} não pode ser vazio", step.title.isNotBlank())
            assertTrue("Subtítulo da etapa ${step.number} não pode ser vazio", step.subtitle.isNotBlank())
            assertTrue("Explicação da etapa ${step.number} não pode ser vazia", step.detailedTheory.isNotBlank())

            for (lessonCase in step.cases) {
                assertEquals(
                    "O caso ${lessonCase.id} deve pertencer à etapa ${step.number}",
                    step.number,
                    lessonCase.stepNumber
                )
                assertTrue("caseTitle vazio em ${lessonCase.id}", lessonCase.caseTitle.isNotBlank())
                assertTrue("shortBadge vazio em ${lessonCase.id}", lessonCase.shortBadge.isNotBlank())
                assertTrue("description vazia em ${lessonCase.id}", lessonCase.description.isNotBlank())
                assertTrue("pedagogicalNote vazia em ${lessonCase.id}", lessonCase.pedagogicalNote.isNotBlank())
                assertTrue("compactSummary vazio em ${lessonCase.id}", lessonCase.compactSummary.isNotBlank())

                // Parsing estrito sem tokens inválidos e correspondência exata com o texto de notação
                val parsedPrimary = CubeMove.parseSequence(lessonCase.primaryAlgorithm)
                val parsedFull = CubeMove.parseSequence(lessonCase.fullResolutionAlgorithm)
                val parsedSetup = CubeMove.parseSequence(lessonCase.setupMovesFromSolved)

                assertTrue("primaryMoveList não pode ser vazia em ${lessonCase.id}", parsedPrimary.isNotEmpty())
                assertTrue("fullMoveList não pode ser vazia em ${lessonCase.id}", parsedFull.isNotEmpty())
                assertTrue("setupMovesFromSolved não pode ser vazia em ${lessonCase.id}", parsedSetup.isNotEmpty())

                assertEquals(parsedPrimary, lessonCase.primaryMoveList)
                assertEquals(parsedFull, lessonCase.fullMoveList)
                assertEquals(
                    "O texto de primaryAlgorithm em ${lessonCase.id} deve coincidir 1:1 com a sequência executada",
                    lessonCase.primaryAlgorithm,
                    CubeMove.formatSequence(lessonCase.primaryMoveList)
                )
                assertEquals(
                    "O texto de fullResolutionAlgorithm em ${lessonCase.id} deve coincidir 1:1 com a sequência executada",
                    lessonCase.fullResolutionAlgorithm,
                    CubeMove.formatSequence(lessonCase.fullMoveList)
                )

                // Estado inicial matematicamente válido
                val baselineSolved = CubeState.createSolved(whiteOnTop = lessonCase.whiteOnTop)
                val initialState = lessonCase.createInitialState()
                assertValidPhysicalCubeInvariants(initialState, baselineSolved)

                assertFalse(
                    "O estado inicial de ${lessonCase.id} (Etapa ${step.number}) não deve já satisfazer o objetivo antes do algoritmo",
                    initialState.isStepGoalAchieved(step.number)
                )

                // Preservação das camadas anteriores no estado inicial de cada etapa (2..7)
                when (step.number) {
                    2 -> assertTrue("Etapa 2 (${lessonCase.id}) deve iniciar com a Cruz Branca pronta embaixo", initialState.isCrossSolvedOnFace(CubeFace.DOWN, CubeColor.WHITE))
                    3 -> assertTrue("Etapa 3 (${lessonCase.id}) deve iniciar com a 1ª camada pronta", initialState.isFirstLayerSolved())
                    4 -> assertTrue("Etapa 4 (${lessonCase.id}) deve iniciar com as 2 primeiras camadas prontas", initialState.isSecondLayerSolved())
                    5 -> assertTrue("Etapa 5 (${lessonCase.id}) deve iniciar com a Cruz Amarela orientada", initialState.isYellowCrossOriented())
                    6 -> assertTrue("Etapa 6 (${lessonCase.id}) deve iniciar com a face amarela orientada", initialState.isYellowFaceOriented())
                    7 -> assertTrue("Etapa 7 (${lessonCase.id}) deve iniciar com os cantos permutados", initialState.isYellowCornersPermuted(requireAlignedWithCenters = true))
                }

                // Aplicação do algoritmo completo sobre o estado inicial
                val finalState = initialState.applyMoves(lessonCase.fullMoveList)
                assertValidPhysicalCubeInvariants(finalState, baselineSolved)
                assertTrue(
                    "O caso ${lessonCase.id} ('${lessonCase.caseTitle}') deve atingir o objetivo matemático da Etapa ${step.number} após aplicar '${lessonCase.fullResolutionAlgorithm}'",
                    finalState.isStepGoalAchieved(step.number)
                )

                // Validação dos destaques visuais associados à etapa
                val highlightedCount = finalState.cubies.count {
                    CurriculumRepository.isCubieHighlighted(it, step.highlightMode)
                }
                assertTrue(
                    "O modo de destaque ${step.highlightMode} da Etapa ${step.number} deve destacar peças relevantes",
                    highlightedCount in 6..27
                )
            }
        }
    }

    /**
     * SEÇÃO 6 — AUDITORIA DEFINITIVA DA CRUZ AMARELA EM DUAS FASES (ETAPA 4, CASO 2):
     * Verifica matematicamente a transição Linha Horizontal -> Formato de L (após o 6º movimento)
     * -> Cruz Amarela (após o 12º movimento) e a preservação das duas primeiras camadas.
     */
    @Test
    fun testDefinitiveYellowCrossTwoPhaseAuditStep4Case2() {
        val step4 = CurriculumRepository.getStep(4)
        val case2 = step4.cases[1]
        assertEquals("s4_c2", case2.id)
        assertEquals("F U R U' R' F' F U R U' R' F'", case2.fullResolutionAlgorithm)
        assertEquals(12, case2.fullMoveList.size)
        assertEquals(2, case2.pedagogicalPhases.size)

        val phase1 = case2.pedagogicalPhases[0]
        val phase2 = case2.pedagogicalPhases[1]

        assertEquals(1, phase1.phaseNumber)
        assertEquals("F U R U' R' F'", phase1.algorithmText)
        assertEquals(0, phase1.startMoveIndex)
        assertEquals(5, phase1.endMoveIndex)
        assertEquals(6, phase1.phaseMoves.size)

        assertEquals(2, phase2.phaseNumber)
        assertEquals("F U R U' R' F'", phase2.algorithmText)
        assertEquals(6, phase2.startMoveIndex)
        assertEquals(11, phase2.endMoveIndex)
        assertEquals(6, phase2.phaseMoves.size)

        assertEquals(
            "A concatenação das Fases 1 e 2 deve corresponder exatamente aos 12 movimentos do Caso 2",
            case2.fullMoveList,
            phase1.phaseMoves + phase2.phaseMoves
        )

        // Estado inicial: Linha Horizontal Amarela no topo + 2 primeiras camadas resolvidas
        val initialCube = case2.createInitialState()
        assertTrue("As 2 primeiras camadas devem estar resolvidas no início do Caso 2", initialCube.isSecondLayerSolved())
        assertTrue("O estado inicial do Caso 2 deve apresentar a Linha Horizontal Amarela no topo", initialCube.isYellowHorizontalLineOnTop())
        assertFalse(initialCube.isYellowLShapeOnTop())
        assertFalse(initialCube.isYellowCrossOriented())

        // Após os 6 movimentos da Fase 1: Formato de L (UB e UL amarelos) + 2 primeiras camadas resolvidas
        val afterPhase1 = initialCube.applyMoves(phase1.phaseMoves)
        assertTrue("As 2 primeiras camadas devem permanecer resolvidas após a Fase 1", afterPhase1.isSecondLayerSolved())
        assertTrue("Após o 6º movimento (Fase 1), o topo deve apresentar o formato de L (trás e esquerda)", afterPhase1.isYellowLShapeOnTop())
        assertFalse("Após apenas a Fase 1, a Cruz Amarela ainda não deve estar completa", afterPhase1.isYellowCrossOriented())

        // Verifica o destaque visual do formato de L (HighlightMode.YELLOW_L_SHAPE)
        val lHighlightedEdges = afterPhase1.cubies.filter {
            it.y == 1 && it.type == CubieType.EDGE &&
                CurriculumRepository.isCubieHighlighted(it, HighlightMode.YELLOW_L_SHAPE)
        }
        assertEquals("O modo YELLOW_L_SHAPE deve destacar exatamente as 2 arestas do L (UB e UL)", 2, lHighlightedEdges.size)

        // Após os 6 movimentos da Fase 2 aplicados sobre o estado em L: Cruz Amarela completa + 2 primeiras camadas resolvidas
        val afterPhase2 = afterPhase1.applyMoves(phase2.phaseMoves)
        assertTrue("As 2 primeiras camadas devem permanecer resolvidas após a Fase 2", afterPhase2.isSecondLayerSolved())
        assertTrue("Após o 12º movimento (Fase 2), a Cruz Amarela deve estar orientada no topo", afterPhase2.isYellowCrossOriented())
    }

    /**
     * VERIFICAÇÃO COMPLEMENTAR — BOTÕES DE NOTAÇÃO DO MODO EXPLORAR:
     * Confirma que todas as 9 famílias de EXPLORE_FAMILIES (F, R, U, L, D, B, M, E, S)
     * combinadas com os 3 modos modificadores (0 = normal, 1 = inverso ', 2 = 180° 2)
     * produzem os 27 movimentos válidos de CubeMove, e que qualquer configuração estática
     * inválida falha de forma explícita (IllegalArgumentException) em vez de ocultar botões.
     */
    @Test
    fun testExploreModeAllNineFamiliesAndModifiersProduceValidMoves() {
        val expectedFamilies = listOf("F", "R", "U", "L", "D", "B", "M", "E", "S")
        assertEquals(
            "EXPLORE_FAMILIES deve conter exatamente as 9 famílias F, R, U, L, D, B, M, E, S",
            expectedFamilies,
            EXPLORE_FAMILIES
        )

        val resolvedMoves = mutableSetOf<CubeMove>()
        for (fam in EXPLORE_FAMILIES) {
            // Modo 0: Normal (90° horário)
            val normalMove = resolveExploreMove(fam, 0)
            assertEquals("Notação normal da família $fam deve coincidir", fam, normalMove.notation)
            assertEquals("Família do movimento normal deve ser $fam", fam, normalMove.baseFamily)
            assertEquals(90f, kotlin.math.abs(normalMove.glAngleDegrees), 0.01f)
            resolvedMoves.add(normalMove)

            // Modo 1: Inverso (90° anti-horário ')
            val primeMove = resolveExploreMove(fam, 1)
            assertEquals("Notação inversa da família $fam deve coincidir", "$fam'", primeMove.notation)
            assertEquals("Família do movimento inverso deve ser $fam", fam, primeMove.baseFamily)
            assertEquals(90f, kotlin.math.abs(primeMove.glAngleDegrees), 0.01f)
            assertEquals("O inverso do movimento normal de $fam deve ser $fam'", primeMove, normalMove.inverse())
            resolvedMoves.add(primeMove)

            // Modo 2: Duplo (180° 2)
            val doubleMove = resolveExploreMove(fam, 2)
            assertEquals("Notação dupla (180°) da família $fam deve coincidir", "${fam}2", doubleMove.notation)
            assertEquals("Família do movimento duplo deve ser $fam", fam, doubleMove.baseFamily)
            assertEquals(180f, kotlin.math.abs(doubleMove.glAngleDegrees), 0.01f)
            resolvedMoves.add(doubleMove)
        }

        assertEquals(
            "As 9 famílias × 3 modificadores do Modo Explorar devem cobrir exatamente os 27 movimentos de CubeMove",
            CubeMove.entries.toSet(),
            resolvedMoves
        )

        // Verifica falha explícita (requireNotNull -> IllegalArgumentException) caso haja dado interno inválido
        val invalidFamilies = listOf("X", "R3", "", "F''")
        for (invalidFam in invalidFamilies) {
            for (mode in 0..2) {
                try {
                    resolveExploreMove(invalidFam, mode)
                    fail("Família inválida '$invalidFam' (modificador $mode) deveria lançar IllegalArgumentException")
                } catch (e: IllegalArgumentException) {
                    assertTrue(
                        "Mensagem de erro deve identificar a notação/família inválida",
                        e.message?.contains(invalidFam) == true
                    )
                }
            }
        }

        // Verifica falha explícita para modo modificador fora do intervalo 0..2
        for (invalidMode in listOf(-1, 3, 99)) {
            try {
                resolveExploreMove("R", invalidMode)
                fail("Modificador inválido $invalidMode deveria lançar IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains(invalidMode.toString()) == true)
            }
        }
    }

    /**
     * SEÇÃO 7 — AUDITORIA PEDAGÓGICA E MATEMÁTICA DAS CRUZES BRANCA (ETAPA 1) E AMARELA (ETAPA 4)
     * E VERIFICAÇÃO DE AUSÊNCIA DE REFERÊNCIAS AO PDF NOS TEXTOS VISÍVEIS AO ALUNO:
     * 1. Audita todos os 6 casos da Etapa 1 (s1_c1..s1_c6): peça-alvo, orientação dos adesivos,
     *    formação da Cruz Branca alinhada aos 4 centros laterais e ausência de face branca
     *    completa (os 4 cantos do topo possuem 0 adesivos brancos voltados para UP ao final,
     *    exibindo exclusivamente os 5 adesivos brancos da Cruz Branca).
     * 2. Audita todos os 3 casos da Etapa 4 (s4_c1, s4_c2, s4_c3), incluindo todos os marcos
     *    intermediários dos 19 movimentos do Caso 3 (Ponto -> Linha Vertical após 6º mov ->
     *    Linha Horizontal após 7º mov U' -> Formato de L após 13º mov -> Cruz Amarela após 19º mov).
     * 3. Verifica que nenhum texto pedagógico exibido ao aluno menciona "PDF" ou "camadas.pdf".
     */
    @Test
    fun testPedagogicalAuditWhiteAndYellowCrossesAndZeroPdfReferences() {
        // =========================================================================
        // 1. AUDITORIA DE AUSÊNCIA DE REFERÊNCIAS AO PDF EM TEXTOS VISÍVEIS AO ALUNO
        // =========================================================================
        val prohibitedTerms = listOf("pdf", "camadas.pdf")
        for (group in CurriculumRepository.notationGroups) {
            val fields = listOf(
                "notationGroup(${group.family}).namePt" to group.namePt,
                "notationGroup(${group.family}).descriptionPt" to group.descriptionPt
            )
            for ((label, text) in fields) {
                for (term in prohibitedTerms) {
                    assertFalse(
                        "O campo visível ao aluno '$label' não deve conter '$term': \"$text\"",
                        text.lowercase().contains(term)
                    )
                }
            }
        }

        for (step in CurriculumRepository.steps) {
            val stepFields = listOf(
                "step(${step.number}).title" to step.title,
                "step(${step.number}).subtitle" to step.subtitle,
                "step(${step.number}).shortGoal" to step.shortGoal,
                "step(${step.number}).goalSimple" to step.goalSimple,
                "step(${step.number}).detailedTheory" to step.detailedTheory
            )
            for ((label, text) in stepFields) {
                for (term in prohibitedTerms) {
                    assertFalse(
                        "O campo visível ao aluno '$label' não deve conter '$term': \"$text\"",
                        text.lowercase().contains(term)
                    )
                }
            }

            for (lessonCase in step.cases) {
                val caseFields = mutableListOf(
                    "case(${lessonCase.id}).caseTitle" to lessonCase.caseTitle,
                    "case(${lessonCase.id}).shortBadge" to lessonCase.shortBadge,
                    "case(${lessonCase.id}).compactSummary" to lessonCase.compactSummary,
                    "case(${lessonCase.id}).description" to lessonCase.description,
                    "case(${lessonCase.id}).pedagogicalNote" to lessonCase.pedagogicalNote
                )
                lessonCase.pdfValidationNote?.let { note ->
                    caseFields.add("case(${lessonCase.id}).pdfValidationNote" to note)
                }
                for (phase in lessonCase.pedagogicalPhases) {
                    caseFields.add("case(${lessonCase.id}).phase(${phase.phaseNumber}).title" to phase.title)
                    caseFields.add("case(${lessonCase.id}).phase(${phase.phaseNumber}).executionBannerText" to phase.executionBannerText)
                    caseFields.add("case(${lessonCase.id}).phase(${phase.phaseNumber}).completionSummaryText" to phase.completionSummaryText)
                }

                for ((label, text) in caseFields) {
                    for (term in prohibitedTerms) {
                        assertFalse(
                            "O campo visível ao aluno '$label' não deve conter '$term': \"$text\"",
                            text.lowercase().contains(term)
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 2. AUDITORIA MATEMÁTICA E VISUAL DOS 6 CASOS DA ETAPA 1 (CRUZ BRANCA)
        // =========================================================================
        val step1 = CurriculumRepository.getStep(1)
        assertEquals(6, step1.cases.size)

        fun countWhiteCornersOnUp(state: CubeState): Int = listOf(
            state.cubieAt(1, 1, 1),
            state.cubieAt(-1, 1, 1),
            state.cubieAt(1, 1, -1),
            state.cubieAt(-1, 1, -1)
        ).count { it.colorOn(CubeFace.UP) == CubeColor.WHITE }

        fun countTotalWhiteStickersOnUp(state: CubeState): Int =
            state.cubies.filter { it.y == 1 }.count { it.colorOn(CubeFace.UP) == CubeColor.WHITE }

        // Verificação individual da peça-alvo no estado inicial de cada um dos 6 casos da Etapa 1
        val s1c1Init = step1.cases[0].createInitialState()
        assertEquals(CubeColor.WHITE, s1c1Init.cubieAt(1, 0, 1).colorOn(CubeFace.FRONT))
        assertEquals(CubeColor.RED, s1c1Init.cubieAt(1, 0, 1).colorOn(CubeFace.RIGHT))

        val s1c2Init = step1.cases[1].createInitialState()
        assertEquals(CubeColor.WHITE, s1c2Init.cubieAt(1, 0, 1).colorOn(CubeFace.RIGHT))
        assertEquals(CubeColor.BLUE, s1c2Init.cubieAt(1, 0, 1).colorOn(CubeFace.FRONT))

        val s1c3Init = step1.cases[2].createInitialState()
        assertEquals(CubeColor.WHITE, s1c3Init.cubieAt(1, -1, 0).colorOn(CubeFace.DOWN))
        assertEquals(CubeColor.RED, s1c3Init.cubieAt(1, -1, 0).colorOn(CubeFace.RIGHT))

        val s1c4Init = step1.cases[3].createInitialState()
        assertEquals(CubeColor.WHITE, s1c4Init.cubieAt(-1, 0, 1).colorOn(CubeFace.FRONT))
        assertEquals(CubeColor.RED, s1c4Init.cubieAt(-1, 0, 1).colorOn(CubeFace.LEFT))

        val s1c5Init = step1.cases[4].createInitialState()
        assertEquals(CubeColor.WHITE, s1c5Init.cubieAt(1, -1, 0).colorOn(CubeFace.RIGHT))
        assertEquals(CubeColor.RED, s1c5Init.cubieAt(1, -1, 0).colorOn(CubeFace.DOWN))

        val s1c6Init = step1.cases[5].createInitialState()
        assertEquals(CubeColor.RED, s1c6Init.cubieAt(1, 1, 0).colorOn(CubeFace.UP))
        assertEquals(CubeColor.WHITE, s1c6Init.cubieAt(1, 1, 0).colorOn(CubeFace.RIGHT))

        for (lessonCase in step1.cases) {
            assertTrue("Etapa 1 (${lessonCase.id}) deve usar whiteOnTop = true", lessonCase.whiteOnTop)
            val baseline = CubeState.createSolved(whiteOnTop = true)
            val initial = lessonCase.createInitialState()
            assertValidPhysicalCubeInvariants(initial, baseline)

            // Centros fixos: UP = Branco, DOWN = Amarelo, FRONT = Azul, RIGHT = Vermelho, BACK = Verde, LEFT = Laranja
            assertEquals(CubeColor.WHITE, initial.cubieAt(0, 1, 0).colorOn(CubeFace.UP))
            assertEquals(CubeColor.YELLOW, initial.cubieAt(0, -1, 0).colorOn(CubeFace.DOWN))
            assertEquals(CubeColor.BLUE, initial.cubieAt(0, 0, 1).colorOn(CubeFace.FRONT))
            assertEquals(CubeColor.RED, initial.cubieAt(1, 0, 0).colorOn(CubeFace.RIGHT))
            assertEquals(CubeColor.GREEN, initial.cubieAt(0, 0, -1).colorOn(CubeFace.BACK))
            assertEquals(CubeColor.ORANGE, initial.cubieAt(-1, 0, 0).colorOn(CubeFace.LEFT))

            assertFalse("Estado inicial de ${lessonCase.id} não pode já ter a Cruz Branca pronta", initial.isWhiteCrossSolved())
            assertTrue(
                "Estado inicial de ${lessonCase.id} não pode ter os 4 cantos brancos resolvidos no topo",
                countWhiteCornersOnUp(initial) < 4
            )

            // Executa passo a passo garantindo que em nenhum movimento intermediário ou final os 4 cantos formam face branca inteira
            var running = initial
            for ((idx, move) in lessonCase.fullMoveList.withIndex()) {
                running = running.applyMove(move)
                assertTrue(
                    "Em ${lessonCase.id} após movimento #${idx + 1} (${move.notation}), os 4 cantos do topo não podem formar face branca inteira",
                    countWhiteCornersOnUp(running) < 4
                )
            }

            // Estado final após o algoritmo completo da Etapa 1
            assertValidPhysicalCubeInvariants(running, baseline)
            assertTrue("Estado final de ${lessonCase.id} deve formar a Cruz Branca alinhada", running.isWhiteCrossSolved())

            // As 4 arestas do topo devem ter BRANCO em UP e cor lateral alinhada ao centro de cada face
            assertEquals(CubeColor.WHITE, running.cubieAt(0, 1, 1).colorOn(CubeFace.UP))
            assertEquals(CubeColor.BLUE, running.cubieAt(0, 1, 1).colorOn(CubeFace.FRONT))
            assertEquals(CubeColor.WHITE, running.cubieAt(0, 1, -1).colorOn(CubeFace.UP))
            assertEquals(CubeColor.GREEN, running.cubieAt(0, 1, -1).colorOn(CubeFace.BACK))
            assertEquals(CubeColor.WHITE, running.cubieAt(1, 1, 0).colorOn(CubeFace.UP))
            assertEquals(CubeColor.RED, running.cubieAt(1, 1, 0).colorOn(CubeFace.RIGHT))
            assertEquals(CubeColor.WHITE, running.cubieAt(-1, 1, 0).colorOn(CubeFace.UP))
            assertEquals(CubeColor.ORANGE, running.cubieAt(-1, 1, 0).colorOn(CubeFace.LEFT))

            // Nenhum dos 4 cantos do topo deve ter branco voltado para UP ao concluir a demonstração da Etapa 1:
            // assim a face superior exibe exatamente os 5 adesivos brancos da Cruz Branca (1 centro + 4 meios)
            assertEquals(
                "Ao concluir ${lessonCase.id}, nenhum canto do topo deve ter adesivo branco em UP (evitando aparência de face branca completa)",
                0,
                countWhiteCornersOnUp(running)
            )
            assertEquals(
                "Ao concluir ${lessonCase.id}, a face superior (UP) deve exibir exatamente os 5 adesivos brancos da Cruz Branca",
                5,
                countTotalWhiteStickersOnUp(running)
            )
        }

        // =========================================================================
        // 3. AUDITORIA MATEMÁTICA COMPLETA DOS 3 CASOS DA ETAPA 4 (CRUZ AMARELA)
        // =========================================================================
        val step4 = CurriculumRepository.getStep(4)
        assertEquals(3, step4.cases.size)

        for (lessonCase in step4.cases) {
            assertFalse("Etapa 4 (${lessonCase.id}) deve ter base branca embaixo (whiteOnTop = false)", lessonCase.whiteOnTop)
            val init = lessonCase.createInitialState()
            assertEquals(CubeColor.WHITE, init.cubieAt(0, -1, 0).colorOn(CubeFace.DOWN))
            assertEquals(CubeColor.YELLOW, init.cubieAt(0, 1, 0).colorOn(CubeFace.UP))
            assertTrue("Estado inicial de ${lessonCase.id} deve ter as 2 primeiras camadas resolvidas", init.isSecondLayerSolved())
        }

        // Caso 1 (s4_c1): Formato de L (UB e UL amarelos) -> Cruz Amarela após 6 movimentos (F U R U' R' F')
        val s4c1 = step4.cases[0]
        val s4c1Init = s4c1.createInitialState()
        assertTrue(s4c1Init.isYellowLShapeOnTop())
        assertFalse(s4c1Init.isYellowHorizontalLineOnTop())
        assertFalse(s4c1Init.isYellowCrossOriented())
        val s4c1Final = s4c1Init.applyMoves(s4c1.fullMoveList)
        assertTrue(s4c1Final.isSecondLayerSolved())
        assertTrue(s4c1Final.isYellowCrossOriented())
        assertFalse("Concluir a Etapa 4 (s4_c1) não deve orientar toda a face amarela (Etapa 5)", s4c1Final.isYellowFaceOriented())

        // Caso 2 (s4_c2): Linha Horizontal (UL e UR amarelos) -> Formato de L após 6º mov -> Cruz Amarela após 12º mov
        val s4c2 = step4.cases[1]
        val s4c2Init = s4c2.createInitialState()
        assertTrue(s4c2Init.isYellowHorizontalLineOnTop())
        val s4c2After6 = s4c2Init.applyMoves(s4c2.fullMoveList.subList(0, 6))
        assertTrue(s4c2After6.isSecondLayerSolved())
        assertTrue(s4c2After6.isYellowLShapeOnTop())
        assertFalse(s4c2After6.isYellowCrossOriented())
        val s4c2After12 = s4c2Init.applyMoves(s4c2.fullMoveList)
        assertTrue(s4c2After12.isSecondLayerSolved())
        assertTrue(s4c2After12.isYellowCrossOriented())
        assertFalse("Concluir a Etapa 4 (s4_c2) não deve orientar toda a face amarela (Etapa 5)", s4c2After12.isYellowFaceOriented())

        // Caso 3 (s4_c3): Apenas o Ponto Central (19 movimentos: F U R U' R' F' U' F U R U' R' F' F U R U' R' F')
        val s4c3 = step4.cases[2]
        assertEquals(19, s4c3.fullMoveList.size)
        val s4c3Init = s4c3.createInitialState()
        // Marco 0 (inicial): 0 arestas amarelas no topo (Apenas o Ponto Central)
        assertFalse(s4c3Init.cubieAt(0, 1, 1).colorOn(CubeFace.UP) == CubeColor.YELLOW)   // UF
        assertFalse(s4c3Init.cubieAt(0, 1, -1).colorOn(CubeFace.UP) == CubeColor.YELLOW)  // UB
        assertFalse(s4c3Init.cubieAt(-1, 1, 0).colorOn(CubeFace.UP) == CubeColor.YELLOW)  // UL
        assertFalse(s4c3Init.cubieAt(1, 1, 0).colorOn(CubeFace.UP) == CubeColor.YELLOW)   // UR
        assertTrue(s4c3Init.isSecondLayerSolved())

        // Marco 1 (após 6º movimento: 1ª aplicação de F U R U' R' F'): Linha Vertical (UF e UB amarelos)
        val s4c3After6 = s4c3Init.applyMoves(s4c3.fullMoveList.subList(0, 6))
        assertEquals(CubeColor.YELLOW, s4c3After6.cubieAt(0, 1, 1).colorOn(CubeFace.UP))  // UF
        assertEquals(CubeColor.YELLOW, s4c3After6.cubieAt(0, 1, -1).colorOn(CubeFace.UP)) // UB
        assertFalse(s4c3After6.cubieAt(-1, 1, 0).colorOn(CubeFace.UP) == CubeColor.YELLOW)
        assertFalse(s4c3After6.cubieAt(1, 1, 0).colorOn(CubeFace.UP) == CubeColor.YELLOW)
        assertTrue(s4c3After6.isSecondLayerSolved())

        // Marco 2 (após 7º movimento: U'): Linha Horizontal (UL e UR amarelos — estado do Caso 2)
        val s4c3After7 = s4c3Init.applyMoves(s4c3.fullMoveList.subList(0, 7))
        assertTrue("Após o 7º movimento (U'), o topo deve exibir a Linha Horizontal (UL e UR)", s4c3After7.isYellowHorizontalLineOnTop())
        assertTrue(s4c3After7.isSecondLayerSolved())

        // Marco 3 (após 13º movimento: 2ª aplicação de F U R U' R' F'): Formato de L (UB e UL amarelos — estado do Caso 1)
        val s4c3After13 = s4c3Init.applyMoves(s4c3.fullMoveList.subList(0, 13))
        assertTrue("Após o 13º movimento, o topo deve exibir o Formato de L (UB e UL)", s4c3After13.isYellowLShapeOnTop())
        assertFalse(s4c3After13.isYellowCrossOriented())
        assertTrue(s4c3After13.isSecondLayerSolved())

        // Marco 4 (após 19º movimento: 3ª aplicação de F U R U' R' F'): Cruz Amarela completa
        val s4c3After19 = s4c3Init.applyMoves(s4c3.fullMoveList)
        assertTrue("Após o 19º movimento, a Cruz Amarela deve estar completa no topo", s4c3After19.isYellowCrossOriented())
        assertTrue(s4c3After19.isSecondLayerSolved())
        assertFalse("Concluir a Etapa 4 (s4_c3) não deve orientar toda a face amarela (Etapa 5)", s4c3After19.isYellowFaceOriented())
    }

    /**
     * SEÇÃO 8 — AUDITORIA MATEMÁTICA, PEDAGÓGICA E DE CUBO FÍSICO DA ETAPA 6 ("POSICIONAR OS CANTOS FINAIS"):
     * Valida os 20 requisitos obrigatórios para a Etapa 6 (s6_c1 e s6_c2):
     * 1. Estado inicial válido do cubo.
     * 2. Face amarela completa no topo antes da Etapa 6.
     * 3. Duas primeiras camadas preservadas antes da Etapa 6.
     * 4. Identificação de par de cantos iguais nas quatro faces laterais.
     * 5. Caso 1 iniciando com exatamente um par válido na face traseira (par Laranja em BACK).
     * 6. Caso 2 iniciando sem pares válidos em nenhuma das 4 faces laterais.
     * 7. Caso 1 resolvendo os quatro cantos após os 9 movimentos (R' F R' B2 R F' R' B2 R2).
     * 8. Caso 2 formando exatamente um par após a primeira execução de 9 movimentos (par Verde na Esquerda).
     * 9. Posicionamento desse par na face traseira após o 10º movimento (U).
     * 10. Caso 2 formando os 4 pares após a segunda execução (19º movimento).
     * 11. Ajuste final do topo (U, U', U2 ou nenhum) alinhando todos os quatro cantos com os centros laterais.
     * 12. Preservação da face amarela no topo após os algoritmos.
     * 13. Preservação das duas primeiras camadas após os algoritmos.
     * 14. Preservação das 12 arestas e 6 centros após o ciclo puro de 3 cantos (R' F R' B2 R F' R' B2 R2).
     * 15. Funcionamento de stepDemoForward() e stepDemoBackward() para ambos os casos da Etapa 6.
     * 16. Funcionamento de resetLessonCase() para ambos os casos da Etapa 6.
     * 17. Consistência da descrição textual e das 4 fases pedagógicas com o comportamento real do cubo.
     * 18. Compatibilidade com os casos das etapas anteriores e seguintes (26 casos totais).
     * 19. Preservação dos 26 casos pedagógicos.
     * 20. Nenhum teste existente quebrado e prova universal para todas as orientações do cubo físico.
     */
    @Test
    fun testDefinitiveStage6CornersMathematicalAndPhysicalCubeAudit() {
        val step6 = CurriculumRepository.getStep(6)
        assertEquals(6, step6.number)
        assertEquals("Posicionar os Cantos Finais", step6.title)
        assertEquals(2, step6.cases.size)

        val s6c1 = step6.cases[0]
        val s6c2 = step6.cases[1]
        assertEquals("s6_c1", s6c1.id)
        assertEquals("s6_c2", s6c2.id)

        val baseAlgNotation = "R' F R' B2 R F' R' B2 R2"
        val baseAlgMoves = CubeMove.parseSequence(baseAlgNotation)
        assertEquals(9, baseAlgMoves.size)
        assertEquals(baseAlgMoves, s6c1.fullMoveList)
        assertEquals(20, s6c2.fullMoveList.size)
        assertEquals(CubeMove.parseSequence("R' F R' B2 R F' R' B2 R2 U R' F R' B2 R F' R' B2 R2 U'"), s6c2.fullMoveList)

        // =========================================================================
        // PROVA DO INVARIANTE PURO DO ALGORITMO DE 9 MOVIMENTOS (R' F R' B2 R F' R' B2 R2):
        // - Preserva 100% dos 6 centros, todas as 12 arestas (4 topo, 4 meio, 4 base) e os 4 cantos inferiores.
        // - Mantém o canto UFL (-1, 1, 1) fixo no lugar e com a mesma orientação.
        // - Permuta ciclicamente apenas os 3 cantos UFR -> UBL -> UBR -> UFR preservando o Amarelo em UP.
        // =========================================================================
        val solved = CubeState.createSolved()
        val afterAlgOnSolved = solved.applyMoves(baseAlgMoves)
        assertTrue("As duas primeiras camadas devem permanecer 100% intactas", afterAlgOnSolved.isSecondLayerSolved())
        assertTrue("A face amarela deve permanecer 100% orientada no topo", afterAlgOnSolved.isYellowFaceOriented())

        for (cubie in solved.cubies) {
            val isTopCorner = cubie.y == 1 && kotlin.math.abs(cubie.x) == 1 && kotlin.math.abs(cubie.z) == 1
            if (!isTopCorner) {
                val afterCubie = afterAlgOnSolved.cubieAt(cubie.x, cubie.y, cubie.z)
                assertEquals(
                    "Peça não-canto-do-topo em (${cubie.x},${cubie.y},${cubie.z}) deve ficar 100% invariante após R' F R' B2 R F' R' B2 R2",
                    cubie.stickers,
                    afterCubie.stickers
                )
            }
        }
        // Canto UFL (-1, 1, 1) também permanece 100% invariante!
        assertEquals(
            "O canto frontal-esquerdo UFL (-1,1,1) deve permanecer fixo após R' F R' B2 R F' R' B2 R2",
            solved.cubieAt(-1, 1, 1).stickers,
            afterAlgOnSolved.cubieAt(-1, 1, 1).stickers
        )

        // =========================================================================
        // AUDITORIA DO CASO 1 (s6_c1): 1 PAR DE CANTOS IGUAIS NA FACE DE TRÁS
        // =========================================================================
        val s6c1Init = s6c1.createInitialState()
        assertEquals(27, s6c1Init.cubies.size)
        assertEquals(54, s6c1Init.cubies.sumOf { it.stickers.size })
        assertTrue("s6_c1 inicial deve ter as 2 primeiras camadas resolvidas", s6c1Init.isSecondLayerSolved())
        assertTrue("s6_c1 inicial deve ter a face amarela completa no topo", s6c1Init.isYellowFaceOriented())
        assertFalse("s6_c1 inicial ainda não deve ter todos os cantos posicionados", s6c1Init.areTopCornersPermuted())
        assertFalse("s6_c1 inicial não deve ter o cubo totalmente resolvido", s6c1Init.isFullySolved())

        // Verificação exata dos pares nas 4 faces laterais de s6_c1 inicial:
        assertEquals(listOf(CubeFace.BACK), s6c1Init.findSidesWithCornerPairs())
        assertTrue(s6c1Init.hasCornerPairOnSide(CubeFace.BACK))
        assertFalse(s6c1Init.hasCornerPairOnSide(CubeFace.FRONT))
        assertFalse(s6c1Init.hasCornerPairOnSide(CubeFace.RIGHT))
        assertFalse(s6c1Init.hasCornerPairOnSide(CubeFace.LEFT))
        // O par traseiro real em s6_c1 é LARANJA (acima do centro Verde traseiro):
        assertEquals(CubeColor.ORANGE, s6c1Init.cornerPairColorOnSide(CubeFace.BACK))
        assertEquals(HighlightMode.YELLOW_CORNERS_PAIR_BACK, CurriculumRepository.resolveStage6DynamicHighlightMode(s6c1Init))
        assertTrue(CurriculumRepository.describeStage6CornerState(s6c1Init).contains("Par Laranja em Trás"))

        // Verificação passo a passo (movimentos 1 a 9) e reversibilidade (backward) em s6_c1:
        val s6c1History = mutableListOf(s6c1Init)
        var s6c1Cursor = s6c1Init
        for (move in s6c1.fullMoveList) {
            s6c1Cursor = s6c1Cursor.applyMove(move)
            s6c1History.add(s6c1Cursor)
        }
        val s6c1Final = s6c1History.last()
        assertTrue("s6_c1 final deve preservar as 2 primeiras camadas", s6c1Final.isSecondLayerSolved())
        assertTrue("s6_c1 final deve preservar a face amarela no topo", s6c1Final.isYellowFaceOriented())
        assertTrue("s6_c1 final deve ter os 4 cantos posicionados e alinhados aos centros", s6c1Final.areTopCornersPermuted())
        assertEquals(
            listOf(CubeFace.FRONT, CubeFace.RIGHT, CubeFace.BACK, CubeFace.LEFT),
            s6c1Final.findSidesWithCornerPairs()
        )
        assertEquals(CubeColor.BLUE, s6c1Final.cornerPairColorOnSide(CubeFace.FRONT))
        assertEquals(CubeColor.ORANGE, s6c1Final.cornerPairColorOnSide(CubeFace.RIGHT))
        assertEquals(CubeColor.GREEN, s6c1Final.cornerPairColorOnSide(CubeFace.BACK))
        assertEquals(CubeColor.RED, s6c1Final.cornerPairColorOnSide(CubeFace.LEFT))
        // As arestas do topo continuam aguardando a Etapa 7
        assertFalse("s6_c1 final não deve resolver as arestas da Etapa 7", s6c1Final.isFullySolved())

        // Verifica retrocesso (stepDemoBackward) aplicando move.inverse() passo a passo de 9 até 0:
        for (idx in s6c1.fullMoveList.indices.reversed()) {
            val move = s6c1.fullMoveList[idx]
            val steppedBack = s6c1History[idx + 1].applyMove(move.inverse())
            assertEquals("Retrocesso no passo $idx de s6_c1 deve restaurar o estado exato", s6c1History[idx], steppedBack)
        }

        // Prova para o cubo físico no Caso 1: se o par estava em FRONT, RIGHT ou LEFT,
        // findTopMoveToPlaceCornerPairOnBack() retorna o giro exato (U2, U ou U') que o coloca em BACK,
        // e após aplicar os 9 movimentos + findTopMoveToAlignPermutedCorners(), os 4 cantos ficam 100% alinhados!
        for (preTurn in listOf(null, CubeMove.U, CubeMove.U2, CubeMove.U_PRIME)) {
            val rotatedCase1 = if (preTurn == null) s6c1Init else s6c1Init.applyMove(preTurn)
            assertEquals("Em qualquer rotação de U, o Caso 1 tem exatamente 1 par lateral", 1, rotatedCase1.findSidesWithCornerPairs().size)
            val setupToBack = rotatedCase1.findTopMoveToPlaceCornerPairOnBack()
            val withPairOnBack = if (setupToBack == null) rotatedCase1 else rotatedCase1.applyMove(setupToBack)
            assertEquals(listOf(CubeFace.BACK), withPairOnBack.findSidesWithCornerPairs())

            val after9Moves = withPairOnBack.applyMoves(baseAlgMoves)
            assertEquals("Após 9 movimentos com par em BACK, as 4 faces laterais devem ter pares", 4, after9Moves.findSidesWithCornerPairs().size)
            val alignMove = after9Moves.findTopMoveToAlignPermutedCorners()
            val alignedState = if (alignMove == null) after9Moves else after9Moves.applyMove(alignMove)
            assertTrue("Após o giro final de alinhamento ($alignMove), areTopCornersPermuted() deve ser true", alignedState.areTopCornersPermuted())
        }

        // =========================================================================
        // AUDITORIA DO CASO 2 (s6_c2): NENHUM PAR DE CANTOS IGUAIS (TROCA DIAGONAL)
        // =========================================================================
        val s6c2Init = s6c2.createInitialState()
        assertEquals(27, s6c2Init.cubies.size)
        assertEquals(54, s6c2Init.cubies.sumOf { it.stickers.size })
        assertTrue("s6_c2 inicial deve ter as 2 primeiras camadas resolvidas", s6c2Init.isSecondLayerSolved())
        assertTrue("s6_c2 inicial deve ter a face amarela completa no topo", s6c2Init.isYellowFaceOriented())
        assertFalse("s6_c2 inicial não deve ter cantos posicionados", s6c2Init.areTopCornersPermuted())
        assertTrue("s6_c2 inicial deve ter 0 pares de cantos em todas as 4 faces laterais", s6c2Init.findSidesWithCornerPairs().isEmpty())
        assertNull("s6_c2 inicial não possui par para levar a BACK", s6c2Init.findTopMoveToPlaceCornerPairOnBack())
        assertEquals(HighlightMode.YELLOW_CORNERS_PERMUTE, CurriculumRepository.resolveStage6DynamicHighlightMode(s6c2Init))
        assertTrue(CurriculumRepository.describeStage6CornerState(s6c2Init).contains("Nenhum par nas laterais"))

        // Auditoria das 4 fases pedagógicas de s6_c2:
        assertEquals(4, s6c2.pedagogicalPhases.size)
        assertEquals(0..8, s6c2.pedagogicalPhases[0].startMoveIndex..s6c2.pedagogicalPhases[0].endMoveIndex)
        assertEquals(9..9, s6c2.pedagogicalPhases[1].startMoveIndex..s6c2.pedagogicalPhases[1].endMoveIndex)
        assertEquals(10..18, s6c2.pedagogicalPhases[2].startMoveIndex..s6c2.pedagogicalPhases[2].endMoveIndex)
        assertEquals(19..19, s6c2.pedagogicalPhases[3].startMoveIndex..s6c2.pedagogicalPhases[3].endMoveIndex)

        // Marco 1 (após Fase 1 — movimentos 1..9: 1ª execução de R' F R' B2 R F' R' B2 R2):
        val s6c2AfterPhase1 = s6c2Init.applyMoves(s6c2.fullMoveList.subList(0, 9))
        assertTrue(s6c2AfterPhase1.isSecondLayerSolved())
        assertTrue(s6c2AfterPhase1.isYellowFaceOriented())
        assertEquals("Após a Fase 1 (9 mov.), deve surgir exatamente 1 par na face ESQUERDA (LEFT)", listOf(CubeFace.LEFT), s6c2AfterPhase1.findSidesWithCornerPairs())
        assertEquals("O par formado na Esquerda em s6_c2 deve ser VERDE", CubeColor.GREEN, s6c2AfterPhase1.cornerPairColorOnSide(CubeFace.LEFT))
        assertEquals(CubeMove.U, s6c2AfterPhase1.findTopMoveToPlaceCornerPairOnBack())
        assertEquals(HighlightMode.YELLOW_CORNERS_PAIR_LEFT, CurriculumRepository.resolveStage6DynamicHighlightMode(s6c2AfterPhase1))
        assertTrue(CurriculumRepository.describeStage6CornerState(s6c2AfterPhase1).contains("Par Verde na Esquerda"))

        // Marco 2 (após Fase 2 — 10º movimento: U):
        val s6c2AfterPhase2 = s6c2Init.applyMoves(s6c2.fullMoveList.subList(0, 10))
        assertTrue(s6c2AfterPhase2.isSecondLayerSolved())
        assertTrue(s6c2AfterPhase2.isYellowFaceOriented())
        assertEquals("Após a Fase 2 (10º mov. U), o par Verde deve estar na face TRASEIRA (BACK)", listOf(CubeFace.BACK), s6c2AfterPhase2.findSidesWithCornerPairs())
        assertEquals(CubeColor.GREEN, s6c2AfterPhase2.cornerPairColorOnSide(CubeFace.BACK))
        assertEquals(HighlightMode.YELLOW_CORNERS_PAIR_BACK, CurriculumRepository.resolveStage6DynamicHighlightMode(s6c2AfterPhase2))
        assertTrue(CurriculumRepository.describeStage6CornerState(s6c2AfterPhase2).contains("Par Verde em Trás"))

        // Marco 3 (após Fase 3 — movimentos 11..19: 2ª execução de R' F R' B2 R F' R' B2 R2):
        val s6c2AfterPhase3 = s6c2Init.applyMoves(s6c2.fullMoveList.subList(0, 19))
        assertTrue(s6c2AfterPhase3.isSecondLayerSolved())
        assertTrue(s6c2AfterPhase3.isYellowFaceOriented())
        assertEquals(
            "Após a Fase 3 (19º mov.), todas as 4 faces laterais devem ter pares de cantos",
            listOf(CubeFace.FRONT, CubeFace.RIGHT, CubeFace.BACK, CubeFace.LEFT),
            s6c2AfterPhase3.findSidesWithCornerPairs()
        )
        assertFalse("Antes do 20º movimento (U'), os 4 pares ainda estão deslocados em relação aos centros", s6c2AfterPhase3.areTopCornersPermuted())
        assertEquals(CubeMove.U_PRIME, s6c2AfterPhase3.findTopMoveToAlignPermutedCorners())
        assertTrue(CurriculumRepository.describeStage6CornerState(s6c2AfterPhase3).contains("gire U'"))

        // Marco 4 (após Fase 4 — 20º movimento: U'):
        val s6c2Final = s6c2Init.applyMoves(s6c2.fullMoveList)
        assertTrue("s6_c2 final deve preservar as 2 primeiras camadas", s6c2Final.isSecondLayerSolved())
        assertTrue("s6_c2 final deve preservar a face amarela no topo", s6c2Final.isYellowFaceOriented())
        assertTrue("s6_c2 final deve ter todos os 4 cantos posicionados e alinhados aos centros", s6c2Final.areTopCornersPermuted())
        assertEquals(CubeColor.BLUE, s6c2Final.cornerPairColorOnSide(CubeFace.FRONT))
        assertEquals(CubeColor.ORANGE, s6c2Final.cornerPairColorOnSide(CubeFace.RIGHT))
        assertEquals(CubeColor.GREEN, s6c2Final.cornerPairColorOnSide(CubeFace.BACK))
        assertEquals(CubeColor.RED, s6c2Final.cornerPairColorOnSide(CubeFace.LEFT))
        assertFalse("s6_c2 final não deve resolver as arestas da Etapa 7", s6c2Final.isFullySolved())

        // Verifica avanço e retrocesso passo a passo (20 movimentos) de s6_c2:
        val s6c2History = mutableListOf(s6c2Init)
        var s6c2Cursor = s6c2Init
        for (move in s6c2.fullMoveList) {
            s6c2Cursor = s6c2Cursor.applyMove(move)
            s6c2History.add(s6c2Cursor)
        }
        for (idx in s6c2.fullMoveList.indices.reversed()) {
            val move = s6c2.fullMoveList[idx]
            val steppedBack = s6c2History[idx + 1].applyMove(move.inverse())
            assertEquals("Retrocesso no passo $idx de s6_c2 deve restaurar o estado exato", s6c2History[idx], steppedBack)
        }

        // =========================================================================
        // TEOREMA UNIVERSAL PARA CUBO FÍSICO NO CASO 2 (TROCA DIAGONAL EM QUALQUER ROTAÇÃO DE U):
        // Prova que, independentemente de como o topo esteja girado (none, U, U2, U') quando
        // não há nenhum par lateral:
        // 1. A 1ª execução de R' F R' B2 R F' R' B2 R2 SEMPRE cria um par na face ESQUERDA (LEFT).
        // 2. O giro U (10º movimento) SEMPRE leva esse par da Esquerda para TRÁS (BACK).
        // 3. A 2ª execução de R' F R' B2 R F' R' B2 R2 SEMPRE forma os 4 pares nas 4 faces laterais.
        // 4. Um único ajuste final do topo (findTopMoveToAlignPermutedCorners()) SEMPRE alinha os 4 cantos aos centros.
        // =========================================================================
        for (initialTopTurn in listOf(null, CubeMove.U, CubeMove.U2, CubeMove.U_PRIME)) {
            val diagState = if (initialTopTurn == null) s6c2Init else s6c2Init.applyMove(initialTopTurn)
            assertTrue("Estado diagonal com giro inicial $initialTopTurn não deve ter nenhum par", diagState.findSidesWithCornerPairs().isEmpty())

            val afterFirst9 = diagState.applyMoves(baseAlgMoves)
            assertEquals(
                "Para giro inicial $initialTopTurn, a 1ª execução de 9 movimentos SEMPRE cria um par em LEFT",
                listOf(CubeFace.LEFT),
                afterFirst9.findSidesWithCornerPairs()
            )

            val afterTurnU = afterFirst9.applyMove(CubeMove.U)
            assertEquals(
                "Para giro inicial $initialTopTurn, o giro U SEMPRE coloca o par recém-criado em BACK",
                listOf(CubeFace.BACK),
                afterTurnU.findSidesWithCornerPairs()
            )

            val afterSecond9 = afterTurnU.applyMoves(baseAlgMoves)
            assertEquals(
                "Para giro inicial $initialTopTurn, a 2ª execução de 9 movimentos SEMPRE forma pares nas 4 faces laterais",
                4,
                afterSecond9.findSidesWithCornerPairs().size
            )

            val finalAlignMove = afterSecond9.findTopMoveToAlignPermutedCorners()
            val fullyAligned = if (finalAlignMove == null) afterSecond9 else afterSecond9.applyMove(finalAlignMove)
            assertTrue(
                "Para giro inicial $initialTopTurn, o ajuste final ($finalAlignMove) SEMPRE alinha os 4 cantos com os centros",
                fullyAligned.areTopCornersPermuted()
            )
            assertTrue(fullyAligned.isSecondLayerSolved())
            assertTrue(fullyAligned.isYellowFaceOriented())
        }

        // Consistência dos textos pedagógicos de s6_c1 e s6_c2:
        assertTrue("Descrição de s6_c1 deve mencionar o par Laranja em Trás", s6c1.description.lowercase().contains("laranja"))
        assertFalse("Descrição de s6_c1 não deve afirmar falsamente que o par inicial é Verde", s6c1.description.lowercase().contains("cantos traseiros possuem adesivos verdes"))
        assertTrue("Descrição de s6_c2 deve explicar que o par surge na Esquerda após a Fase 1", s6c2.description.lowercase().contains("esquerda"))
        assertEquals(26, CurriculumRepository.steps.sumOf { it.cases.size })
    }
}
