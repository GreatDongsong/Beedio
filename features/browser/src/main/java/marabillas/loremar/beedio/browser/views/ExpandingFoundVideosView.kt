/*
 * Beedio is an Android app for downloading videos
 * Copyright (C) 2019 Loremar Marabillas
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package marabillas.loremar.beedio.browser.views

import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.animation.doOnEnd
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.databinding.DataBindingUtil
import androidx.transition.*
import marabillas.loremar.beedio.browser.R
import marabillas.loremar.beedio.browser.databinding.FoundVideosSheetBinding
import kotlin.math.roundToInt

class ExpandingFoundVideosView : FrameLayout, View.OnClickListener {

    var toolbarEventsListener: ToolBarEventsListener? = null

    private lateinit var sheet: ViewGroup
    private lateinit var head: ViewGroup
    private lateinit var body: ViewGroup
    private lateinit var bouncingBug: ImageView
    private lateinit var foundCountText: TextView
    private lateinit var closeBtn: ImageView
    private lateinit var toolbar: ViewGroup
    private lateinit var select: TextView
    private lateinit var cancel: TextView
    private lateinit var all: TextView
    private lateinit var delete: TextView
    private lateinit var queue: TextView
    private lateinit var merge: TextView

    private var isExpanded = false
    private val animationDuration = 200L
    private val bouncingBugHandler = Handler(Looper.getMainLooper())

    constructor(context: Context) : super(context) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initView()
    }

    fun animateBouncingBug(isAnimating: Boolean) {
        val bugDrawable = bouncingBug.drawable as AnimationDrawable
        if (isAnimating) {
            bouncingBugHandler.removeCallbacksAndMessages(null)
            TransitionManager.beginDelayedTransition(head)
            if (bouncingBug.visibility == GONE)
                bouncingBug.visibility = VISIBLE
            bugDrawable.start()
        } else {
            bouncingBugHandler.postDelayed(
                    {
                        TransitionManager.beginDelayedTransition(head)
                        bugDrawable.stop()
                        bugDrawable.selectDrawable(0)
                        if (foundCountText.visibility == GONE || foundCountText.text.isNullOrBlank())
                            bouncingBug.visibility = GONE
                    }, 2000
            )
        }
    }

    fun updateFoundVideosCountText(count: Int) {
        if (count > 0 && foundCountText.visibility == GONE)
            foundCountText.visibility = View.VISIBLE

        val text = resources.getString(R.string.found_videos_count_text, count)
        val spannable = SpannableString(text)
        val countText = text.substringBeforeLast(" VIDEOS")
        val colorSpan = ForegroundColorSpan(Color.WHITE)
        spannable.setSpan(colorSpan, 0, countText.length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        foundCountText.text = spannable
    }

    private fun initView() {
        val inflater = LayoutInflater.from(context)

        val binding = DataBindingUtil.inflate<FoundVideosSheetBinding>(
                inflater, R.layout.found_videos_sheet, null, false)
                .apply {
                    sheet = foundVideosSheet
                    head = foundVideosHead
                    body = foundVideosBody
                    bouncingBug = foundVideosBouncyIcon
                    foundCountText = foundVideosNumFoundText
                    closeBtn = foundVideosCloseBtn
                    toolbar = foundVideosToolbar
                    select = foundVideoMenuSelect
                    cancel = foundVideoMenuCancel
                    all = foundVideoMenuAll
                    delete = foundVideoMenuDelete
                    queue = foundVideoMenuQueue
                    merge = foundVideoMenuMerge
                }

        LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            binding.root.layoutParams = this
            addView(binding.root)
        }

        setupListeners()
    }

    private fun setupListeners() {
        head.setOnClickListener(this)
        closeBtn.setOnClickListener(this)
        select.setOnClickListener(this)
        cancel.setOnClickListener(this)
        all.setOnClickListener(this)
        delete.setOnClickListener(this)
        queue.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v) {
            head -> {
                if (!isExpanded)
                    expand()
                else
                    contract()
            }
            closeBtn -> {
                if (isExpanded)
                    contract()
            }
            select -> {
                showMenu()
                toolbarEventsListener?.onActivateSelection()
            }
            cancel -> {
                hideMenu()
                toolbarEventsListener?.onDeactivateSelection()
            }
            all -> {
                toolbarEventsListener?.onSelectionAll()
            }
            delete -> {
                toolbarEventsListener?.onDeleteAllSelected()
            }
            queue -> {
                toolbarEventsListener?.onQueueAllSelected()
            }
        }
    }

    private fun expand() {
        body.visibility = VISIBLE
        head.layoutTransition = null
        initTransition(this::doOnEndOfExpand)
        sheet.updateLayout { height = MATCH_PARENT }
        expandHead()
    }

    private fun contract() {
        beforeContract()
        initTransition(this::doOnEndOfContract)
        sheet.updateLayout { height = WRAP_CONTENT }
        contractHead()
    }

    private fun initTransition(doOnEnd: () -> Unit) {
        val sheetTransition = ChangeBounds().apply { addTarget(sheet) }
        val contentTransition = ChangeBounds().apply { addTarget(body) }
        val transitionSet = TransitionSet().apply {
            addTransition(sheetTransition)
            addTransition(contentTransition)
            duration = animationDuration
            doOnEnd { doOnEnd() }
        }
        TransitionManager.beginDelayedTransition(this, transitionSet)
    }

    private fun doOnEndOfExpand() {
        isExpanded = true
        head.updateLayout { height = WRAP_CONTENT }
        foundCountText.apply {
            updateLayoutParams<LinearLayoutCompat.LayoutParams> {
                width = 0
                leftMargin = (16 * resources.displayMetrics.density).roundToInt()
            }
            visibility = View.VISIBLE
        }
        closeBtn.visibility = VISIBLE
        toolbar.visibility = View.VISIBLE
    }

    private fun doOnEndOfContract() {
        isExpanded = false
        body.visibility = GONE
        foundCountText.apply {
            updateLayoutParams<LinearLayoutCompat.LayoutParams> {
                leftMargin = 0
                width = WRAP_CONTENT
            }
            if (text.isNullOrBlank())
                visibility = GONE
        }
        closeBtn.visibility = GONE
        head.layoutTransition = LayoutTransition()
    }

    private fun expandHead() {
        head.apply {
            ObjectAnimator.ofInt(this, "left", left, 0).apply {
                duration = animationDuration * 2 / 5
                doOnEnd {
                    updateLayout { width = MATCH_PARENT }
                    val black = ResourcesCompat.getColor(resources, R.color.black1, null)
                    background = ColorDrawable(black)
                    sheet.setPadding(0)
                }
                start()
            }
        }
    }

    private fun contractHead() {
        head.apply {
            head.measure(WRAP_CONTENT, WRAP_CONTENT)
            ObjectAnimator.ofInt(this, "left", 0, head.measuredWidth).apply {
                startDelay = animationDuration * 3 / 5
                duration = animationDuration * 2 / 5
                doOnEnd { updateLayout { width = WRAP_CONTENT } }
                start()
            }
        }
    }

    private fun beforeContract() {
        toolbar.visibility = GONE
        head.apply {
            background = ResourcesCompat.getDrawable(
                    resources, R.drawable.found_videos_head_background, null)
            val topPadding = (14 * resources.displayMetrics.density).roundToInt()
            sheet.setPadding(0, topPadding, 0, 0)
            updateLayout { height = (48 * resources.displayMetrics.density).roundToInt() }
        }
    }

    private fun showMenu() {
        select.visibility = GONE
        val transition = TransitionSet().apply {
            addTransition(Slide(GravityCompat.END))
            addTransition(Fade())
        }
        TransitionManager.beginDelayedTransition(toolbar, transition)
        cancel.visibility = VISIBLE
        all.visibility = VISIBLE
        delete.visibility = VISIBLE
        queue.visibility = VISIBLE
        merge.visibility = VISIBLE
    }

    private fun hideMenu() {
        cancel.visibility = GONE
        val appearTransition = Fade().apply { addTarget(select) }
        val slideTransition = Slide(GravityCompat.END).apply {
            addTarget(all)
            addTarget(delete)
            addTarget(queue)
            addTarget(merge)
        }
        val transitionSet = TransitionSet().apply {
            addTransition(appearTransition)
            addTransition(slideTransition)
        }
        TransitionManager.beginDelayedTransition(toolbar, transitionSet)
        select.visibility = VISIBLE
        all.visibility = GONE
        delete.visibility = GONE
        queue.visibility = GONE
        merge.visibility = GONE
    }

    private fun View.updateLayout(block: ViewGroup.LayoutParams.() -> Unit) {
        updateLayoutParams<ViewGroup.LayoutParams> {
            block()
        }
    }

    private fun Transition.doOnEnd(block: TransitionListenerAdapter.() -> Unit) {
        addListener(object : TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                block()
            }
        })
    }

    interface ToolBarEventsListener {
        fun onActivateSelection()

        fun onDeactivateSelection()

        fun onSelectionAll()

        fun onDeleteAllSelected()

        fun onQueueAllSelected()
    }
}