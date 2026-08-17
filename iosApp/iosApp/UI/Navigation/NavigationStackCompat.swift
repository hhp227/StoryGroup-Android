import SwiftUI

/// iOS 16은 NavigationStack(path:), iOS 15는 재귀 NavigationLink로 같은 path 배열을 재현한다.
/// iOS 15(iPhone 7) 지원 때문에 필요하다 — NavigationStack(path:)은 16+뿐이다.
/// ConCafe iosApp/Presentation/Navigation/NavigationStackCompat.swift 포팅(Route 타입만 StoryGroup 것).
struct NavigationStackCompat<Root: View>: View {
    @Binding private var path: [Route]

    private let root: Root

    private let destination: (Route) -> AnyView

    var body: some View {
        if #available(iOS 16.0, *) {
            NavigationStack(path: $path) {
                root.navigationDestination(for: Route.self) { destination($0) }
            }
        } else {
            LegacyNavigationStack(path: $path, root: root, destination: destination)
        }
    }

    init(
        path: Binding<[Route]>,
        @ViewBuilder root: () -> Root,
        @ViewBuilder destination: @escaping (Route) -> some View
    ) {
        self._path = path
        self.root = root()
        self.destination = { AnyView(destination($0)) }
    }
}

private struct LegacyNavigationStack<Root: View>: View {
    @Binding var path: [Route]

    let root: Root

    let destination: (Route) -> AnyView

    var body: some View {
        NavigationView {
            LegacyNavigationNode(path: $path, depth: 0, root: AnyView(root), destination: destination)
        }
        .navigationViewStyle(StackNavigationViewStyle())
    }
}

/// depth번째 path 원소를 push하고, 자기 자신을 depth+1로 재귀 구성한다.
/// isActive를 끄면(뒤로가기) path를 depth까지 잘라 상태(path)가 진실을 유지한다.
private struct LegacyNavigationNode: View {
    @Binding var path: [Route]

    let depth: Int

    let root: AnyView

    let destination: (Route) -> AnyView

    var body: some View {
        ZStack {
            root
            NavigationLink(
                isActive: Binding(
                    get: { path.count > depth },
                    set: { isActive in
                        if !isActive { path = Array(path.prefix(depth)) }
                    }
                ),
                destination: {
                    if path.count > depth {
                        LegacyNavigationNode(
                            path: $path,
                            depth: depth + 1,
                            root: destination(path[depth]),
                            destination: destination
                        )
                    } else {
                        EmptyView()
                    }
                },
                label: { EmptyView() }
            )
            .hidden()
        }
    }
}
