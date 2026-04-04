import Flutter
import UIKit

class SceneDelegate: FlutterSceneDelegate {

    /// 카카오톡 등 외부 앱에서 커스텀 URL 스킴으로 돌아올 때 호출된다.
    /// Scene 기반 앱에서는 AppDelegate의 open URL이 호출되지 않으므로,
    /// 여기서 받아서 Flutter 엔진(플러그인)에 전달한다.
    /// 카카오 URL은 super를 호출하지 않아 Flutter 라우터로 전달되지 않도록 한다.
    override func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        var handled = false
        for context in URLContexts {
            let url = context.url
            if url.scheme?.hasPrefix("kakao") == true {
                if let appDelegate = UIApplication.shared.delegate as? FlutterAppDelegate {
                    appDelegate.application(
                        UIApplication.shared,
                        open: url,
                        options: [:]
                    )
                }
                handled = true
            }
        }
        // 카카오 URL이 아닌 경우에만 Flutter 라우터에 전달
        if !handled {
            super.scene(scene, openURLContexts: URLContexts)
        }
    }
}
