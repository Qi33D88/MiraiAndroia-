package io.github.mzdluo123.mirai.android.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import io.github.mzdluo123.mirai.android.BuildConfig
import io.github.mzdluo123.mirai.android.R
import io.github.mzdluo123.mirai.android.databinding.FragmentAboutBinding
import kotlinx.android.synthetic.main.fragment_about.*
import splitties.toast.toast

class AboutFragment : Fragment() {
    private lateinit var aboutBinding: FragmentAboutBinding
    private var click = 0
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val aboutBinding = DataBindingUtil.inflate<FragmentAboutBinding>(
            layoutInflater,
            R.layout.fragment_about,
            container,
            false
        )
        aboutBinding.appVersion = requireContext().packageManager.getPackageInfo(
            requireContext().packageName,
            0
        ).versionName
        aboutBinding.coreVersion = BuildConfig.COREVERSION
        aboutBinding.consoleVersion = BuildConfig.CONSOLEVERSION
        return aboutBinding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        github_btn.setOnClickListener {
            openUrl("https://github.com/mamoe/mirai")
        }
        github2_bth.setOnClickListener {
            openUrl("https://github.com/mzdluo123/MiraiAndroid")
        }
        btn_visit_forum.setOnClickListener {
            openUrl("https://mirai.mamoe.net/")
        }
        imageView2.setOnClickListener {
            if (click < 4) {
                click++
                return@setOnClickListener
            }
            imageView2.setImageResource(R.drawable.avatar)
        }
        btn_join_group.setOnClickListener {
            if (!joinQQGroup("df6wSbKtDBo3cMJ9ULtYAZeln5ZZuA9d")) {
                toast("??????QQ????????????????????????????????????QQ")
            }
        }
    }


    private fun openUrl(url: String) {
        val uri = Uri.parse(url)
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    /****************
     *
     * ?????????????????????????????????MiraiAndroid(206073050) ??? key ?????? 2aqIV-MkAOvx53dwUl-VVUYZqn8UrFAJ
     * ?????? joinQQGroup(2aqIV-MkAOvx53dwUl-VVUYZqn8UrFAJ) ???????????????Q????????????????????? MiraiAndroid(206073050)
     *
     * @param key ??????????????????key
     * @return ??????true???????????????Q???????????????false??????????????????
     */
    fun joinQQGroup(key: String): Boolean {
        val intent = Intent()
        intent.data =
            Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D$key")
        // ???Flag??????????????????????????????????????????????????????????????????????????????????????????Q???????????????????????????????????????????????????????????????    //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            // ????????????Q???????????????????????????
            false
        }
    }


}
