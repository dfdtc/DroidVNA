/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Based in part on LibreVNA-GUI (https://github.com/jankae/LibreVNA),
 * Copyright (C) Jan Käberich and contributors, GPL-3.0.
 */

package com.librevna.mobile.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Parametric calibration-standard models used when a .cal / .calkit entry has no Touchstone
 * data (the desktop GUI writes these coefficients for manual kits such as MCB-60M).
 *
 * Formulas follow LibreVNA-GUI Calibration/calstandard.cpp and Util::addTransmissionLine so the
 * reconstructed standards match the desktop calibration.
 */
sealed class CalStandardModel {
    abstract fun s11At(freqHz: Double): Complex
    open fun s21At(freqHz: Double): Complex = Complex.NAN
    open fun s12At(freqHz: Double): Complex = Complex.NAN
    open fun s22At(freqHz: Double): Complex = Complex.NAN

    data class Open(
        val z0: Double,
        val delayPs: Double,
        val loss: Double,
        val c0: Double,
        val c1: Double,
        val c2: Double,
        val c3: Double,
    ) : CalStandardModel() {
        override fun s11At(freqHz: Double): Complex {
            val c = c0 * 1e-15 + c1 * 1e-27 * freqHz + c2 * 1e-36 * freqHz.pow(2) + c3 * 1e-45 * freqHz.pow(3)
            val open = if (c == 0.0) {
                Complex.ONE
            } else {
                val imp = Complex(0.0, -1.0 / (2.0 * PI * freqHz * c))
                (imp - Complex(50.0, 0.0)) / (imp + Complex(50.0, 0.0))
            }
            return addTransmissionLine(open, z0, delayPs, loss, freqHz)
        }
    }

    data class Short(
        val z0: Double,
        val delayPs: Double,
        val loss: Double,
        val l0: Double,
        val l1: Double,
        val l2: Double,
        val l3: Double,
    ) : CalStandardModel() {
        override fun s11At(freqHz: Double): Complex {
            val l = l0 * 1e-12 + l1 * 1e-24 * freqHz + l2 * 1e-33 * freqHz.pow(2) + l3 * 1e-42 * freqHz.pow(3)
            val imp = Complex(0.0, 2.0 * PI * freqHz * l)
            val gamma = (imp - Complex(50.0, 0.0)) / (imp + Complex(50.0, 0.0))
            return addTransmissionLine(gamma, z0, delayPs, loss, freqHz)
        }
    }

    data class Load(
        val z0: Double,
        val delayPs: Double,
        val loss: Double,
        val resistance: Double,
        val cFirst: Boolean,
        val cParallel: Double,
        val lSeries: Double,
    ) : CalStandardModel() {
        override fun s11At(freqHz: Double): Complex {
            var imp = Complex(resistance, 0.0)
            if (cFirst) imp += Complex(0.0, 2.0 * PI * freqHz * lSeries)
            if (cParallel > 0.0) {
                val impC = Complex(0.0, -1.0 / (2.0 * PI * freqHz * cParallel))
                imp = (imp * impC) / (imp + impC)
            }
            if (!cFirst) imp += Complex(0.0, 2.0 * PI * freqHz * lSeries)
            val gamma = (imp - Complex(50.0, 0.0)) / (imp + Complex(50.0, 0.0))
            return addTransmissionLine(gamma, z0, delayPs, loss, freqHz)
        }
    }

    data class Through(
        val z0: Double,
        val delayPs: Double,
        val loss: Double,
    ) : CalStandardModel() {
        private fun params(freqHz: Double): Pair<Complex, Complex> {
            val w = 2.0 * PI * freqHz
            val fSqrt = sqrt(freqHz / 1e9)
            val offsetDelay = delayPs * 1e-12
            val offsetLoss = loss * 1e9
            val zc = Complex(
                z0 + (offsetLoss / (2.0 * w)) * fSqrt,
                -(offsetLoss / (2.0 * w)) * fSqrt,
            )
            val gammaL = Complex(
                offsetLoss * offsetDelay / (2.0 * z0) * fSqrt,
                w * offsetDelay + offsetLoss * offsetDelay / (2.0 * z0) * fSqrt,
            )
            val gamma = (zc - Complex(50.0, 0.0)) / (zc + Complex(50.0, 0.0))
            return gamma to exp(gammaL * -1.0)
        }

        override fun s11At(freqHz: Double): Complex {
            val (gamma, p) = params(freqHz)
            val p2 = p * p
            return gamma * (Complex.ONE - p2) / (Complex.ONE - p2 * gamma * gamma)
        }

        override fun s21At(freqHz: Double): Complex {
            val (gamma, p) = params(freqHz)
            val p2 = p * p
            return p * (Complex.ONE - gamma * gamma) / (Complex.ONE - p2 * gamma * gamma)
        }

        override fun s12At(freqHz: Double): Complex = s21At(freqHz)
        override fun s22At(freqHz: Double): Complex = s11At(freqHz)
    }

    companion object {
        private fun exp(c: Complex): Complex {
            val e = exp(c.re)
            return Complex(e * cos(c.im), e * sin(c.im))
        }

        private fun addTransmissionLine(
            termination: Complex,
            offsetImpedance: Double,
            delayPs: Double,
            loss: Double,
            freqHz: Double,
        ): Complex {
            val w = 2.0 * PI * freqHz
            if (w == 0.0) return termination
            val offsetDelay = delayPs * 1e-12
            val offsetLoss = loss * 1e9
            val fSqrt = sqrt(freqHz / 1e9)
            val zc = Complex(
                offsetImpedance + (offsetLoss / (2.0 * w)) * fSqrt,
                -(offsetLoss / (2.0 * w)) * fSqrt,
            )
            val gammaL = Complex(
                offsetLoss * offsetDelay / (2.0 * offsetImpedance) * fSqrt,
                w * offsetDelay + offsetLoss * offsetDelay / (2.0 * offsetImpedance) * fSqrt,
            )
            val gamma1 = (zc - Complex(50.0, 0.0)) / (zc + Complex(50.0, 0.0))
            val e = exp(gammaL * -2.0)
            val oneMinusE = Complex.ONE - e
            val numerator = gamma1 * (oneMinusE - gamma1 * termination) + e * termination
            val denominator = Complex.ONE - gamma1 * (e * gamma1 + termination * oneMinusE)
            return numerator / denominator
        }
    }
}